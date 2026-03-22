# peer-ledger

Simple peer-to-peer ledger prototype over the built-in Java HTTP server.

The project runs multiple console nodes on different ports. Nodes discover peers, exchange known block hashes, return block data, accept new blocks and transactions, and broadcast them to other peers.

## Stack

- Java
- Gradle
- `com.sun.net.httpserver.HttpServer`
- Jackson
- SLF4J + Lombok

## How To Run

From the project root:

```powershell
.\gradlew.bat runNode8081
```

or:

```powershell
.\gradlew.bat runNode8082
```

or:

```powershell
.\gradlew.bat runNode8083
```

To start all three local nodes at once, see [RUN_NODES.md](/C:/Users/Acer0/IdeaProjects/peer-ledger/RUN_NODES.md).

Bootstrap peers are configured in [peers.json](/C:/Users/Acer0/IdeaProjects/peer-ledger/src/main/resources/peers.json):

```json
[
  "localhost:8081",
  "localhost:8082",
  "localhost:8083"
]
```

## Network Topology

Current default topology is a local 3-node setup:

- `localhost:8081`
- `localhost:8082`
- `localhost:8083`

Each node:

- listens for incoming HTTP requests
- periodically asks known peers for `/addr`
- stores discovered peers in memory
- broadcasts new blocks and transactions to other known peers

## Protocol

### `GET /status`

Returns the current node state.

Response:

```json
{
  "selfAddress": "localhost:8081",
  "peersCount": 3,
  "blocksCount": 1,
  "transactionsCount": 2
}
```

### `GET /addr`

Returns all currently known peers.

Response:

```json
[
  "localhost:8081",
  "localhost:8082",
  "localhost:8083"
]
```

### `GET /getblocks`

Returns all known block hashes.

Response:

```json
[
  "hash1",
  "hash2"
]
```

### `GET /getblocks/{hash}`

Returns block hashes that come after the given hash.

Response:

```json
[
  "nextHash1",
  "nextHash2"
]
```

### `GET /getdata/{hash}`

Returns block data for a specific block hash.

Response:

```json
{
  "hash": "blockHash",
  "data": "block payload"
}
```

### `GET /transactions`

Returns all known transaction hashes.

Response:

```json
[
  "txHash1",
  "txHash2"
]
```

### `GET /transactions/{hash}`

Returns a specific transaction.

Response:

```json
{
  "hash": "txHash",
  "data": "transaction payload"
}
```

### `POST /block`

Accepts a new block and broadcasts it to peers if it was not seen before.

Request body:

```json
{
  "data": "block payload"
}
```

Success response:

```json
{
  "accepted": true,
  "hash": "newBlockHash"
}
```

Possible error responses:

- `400` if `data` is missing
- `405` if method is not `POST`
- `409` if block already exists

### `POST /inv`

Accepts a new transaction and broadcasts it to peers if it was not seen before.

Request body:

```json
{
  "data": "transaction payload"
}
```

Success response:

```json
{
  "accepted": true,
  "hash": "newTransactionHash"
}
```

Possible error responses:

- `400` if `data` is missing
- `405` if method is not `POST`
- `409` if transaction already exists

## Manual Test Scenario

### 1. Start three nodes

- `localhost:8081`
- `localhost:8082`
- `localhost:8083`

### 2. Check discovery

- `GET http://localhost:8081/status`
- `GET http://localhost:8082/status`
- `GET http://localhost:8083/status`
- `GET http://localhost:8081/addr`
- `GET http://localhost:8082/addr`
- `GET http://localhost:8083/addr`

Expected result: each node returns its own status and the known local peers.

### 3. Send a transaction

URL:

- `POST http://localhost:8081/inv`

Body:

```json
{
  "data": "alice->bob:5"
}
```

Check propagation:

- `GET http://localhost:8082/transactions`
- `GET http://localhost:8083/transactions`

### 4. Send a block

URL:

- `POST http://localhost:8081/block`

Body:

```json
{
  "data": "block-001"
}
```

Check propagation:

- `GET http://localhost:8082/getblocks`
- `GET http://localhost:8083/getblocks`

### 5. Read block data

Use the returned block hash:

- `GET http://localhost:8082/getdata/{blockHash}`

## Experiment Notes

Current verified scenario is a local multi-process setup on one machine:

- three nodes on different ports
- peer discovery through `/addr`
- block propagation through `/block`
- transaction propagation through `/inv`

The current implementation stores peers, blocks and transactions in memory only.
