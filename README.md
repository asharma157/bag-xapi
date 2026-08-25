# bag-xapi

Orchestration layer of the [cookie-based per-layer version routing POC](../bag-ui/README.md).
Java 17 / Spring Boot. Exposes `GET /api/bags`, calls `bag-service`, applies totals and
promotions on top of what the backend returns.

**The full POC — the cookie model, the mesh routing, local validation, GKE deployment and the
demo script — is documented in the [bag-ui](../bag-ui/README.md) repo.** This README covers only
what is specific to this service.

## Its version is selected by the `bag_orch` cookie

`bag_orch=2.3` routes a request to pods labelled `version: 2.3`. That routing is done entirely by
Istio; nothing in this codebase selects a version, and the backend URL
(`http://bag-service:8080/api/bags`) is a constant that never encodes one.

Versions shipped: **2.2** (default) and **2.3** — 2.3 applies a `MEMBER10` 10% discount and
quotes a delivery date, so pinning `bag_orch` alone produces a visible change with an unchanged
item list.

## Its one routing responsibility: propagation

Envoy does not copy application headers from an inbound request onto a new outbound one. If this
service drops the `bag_service` cookie when it calls the backend, that hop's sidecar has nothing
to match on and silently serves the default version.

| Concern | Where |
|---|---|
| Inbound capture | [`routing/RoutingContextFilter.java`](src/main/java/com/example/bagxapi/routing/RoutingContextFilter.java) |
| Outbound replay | [`routing/RoutingPropagationInterceptor.java`](src/main/java/com/example/bagxapi/routing/RoutingPropagationInterceptor.java) |
| Where it is registered | [`config/DownstreamConfig.java`](src/main/java/com/example/bagxapi/config/DownstreamConfig.java) — on the shared `RestTemplate`, so every downstream call inherits it |

It forwards `bag_fed`, `bag_orch` and `bag_service` (cookie **and** header form) plus the B3/W3C
tracing headers. It never reads a value to make a decision — it only carries it.

## Run it

```bash
mvn package -DskipTests
APP_VERSION=2.2 BAG_SERVICE_URL=http://localhost:8082 \
  java -jar target/bag-xapi-0.0.1-SNAPSHOT.jar --server.port=8081
```

| Env var | Default | Purpose |
|---|---|---|
| `APP_VERSION` | `2.2` | the version this instance reports; in Kubernetes it comes from the pod's `version` label via the downward API |
| `POD_NAME` | hostname | instance identity, stamped on responses |
| `BAG_SERVICE_URL` | `http://bag-service:8080` | constant backend address; overridable **only** to run off-cluster, never to select a version |

Endpoints: `GET /api/bags`, `GET /health`. Every response carries `x-bag-xapi-version` and
`x-bag-xapi-instance`.

## Deploy

```bash
gcloud builds submit . --tag $REPO/bag-xapi:2.2
sed -i.bak "s#us-central1-docker.pkg.dev/PROJECT_ID/bag-poc#$REPO#g" k8s/deployment-*.yaml
kubectl apply -f k8s/
```

`k8s/` holds one Deployment per version plus a single Service selecting on `app: bag-xapi` only,
so it spans every version. VirtualService and DestinationRule are managed separately — by the
routing controller or Kiali — and are not part of this repo.
