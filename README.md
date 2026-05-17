# peer-ledger

Peer-to-peer ledger prototype on top of Java's built-in HTTP server.

Each node can:

- discover peers via `/addr`
- exchange block and transaction inventories
- pull missing data from peers
- keep a canonical chain separate from the full known block set
- validate structured signed transactions and structured ledger blocks
- mine new structured blocks from pending structured transactions
- persist blocks and node key pairs on disk

## Stack

- Java
- Gradle
- `com.sun.net.httpserver.HttpServer`
- Jackson
- SLF4J
- Lombok
- JUnit 5

## Project Entry Points

Main node process:

```powershell
.\gradlew.bat runNode8081
.\gradlew.bat runNode8082
.\gradlew.bat runNode8083
```

Network simulation:

```powershell
.\gradlew.bat runSimulation
```

Tests:

```powershell
.\gradlew.bat test
```

## Node Startup

Default local bootstrap peers are defined in [src/main/resources/peers.json](/C:/Users/Acer0/IdeaProjects/peer-ledger/src/main/resources/peers.json):

```json
[
  "localhost:8081",
  "localhost:8082",
  "localhost:8083"
]
```

To start one local node:

```powershell
.\gradlew.bat runNode8081
```

To open three separate PowerShell windows, see [RUN_NODES.md](/C:/Users/Acer0/IdeaProjects/peer-ledger/RUN_NODES.md).

`NodeApp` also supports explicit CLI arguments when launched manually from the IDE or another Java entrypoint:

1. `port` - required
2. `host` - default `localhost`
3. `peersConfigPath` - default `peers.json`
4. `backgroundServicesEnabled` - default `true`
5. `broadcastFanOut` - default `0` (`0` means broadcast to all known peers)
6. `miningDifficulty` - default `0`
7. `miningIntervalMillis` - default `2000`
8. `miningReward` - default `1.0`

When background services are enabled, a node automatically runs:

- peer discovery
- block pull sync
- transaction pull sync
- periodic mining of structured transactions

## Persistence

Per-node files are stored under `data/node-<port>/`:

- `blocks.json` - all known blocks
- `keys.json` - generated node key pair

Transactions and known peers are kept in memory. After a canonical-chain rebuild, transactions already included in the canonical chain are removed from the pending transaction store.

## HTTP API

### `GET /status`

Returns the current node snapshot:

```json
{
  "selfAddress": "localhost:8081",
  "peersCount": 3,
  "blocksCount": 1,
  "transactionsCount": 2
}
```

`blocksCount` is the canonical chain size, not the total number of known blocks.

### `GET /addr`

Returns known peers.

### `GET /getblocks`

Returns canonical-chain block hashes in order.

### `GET /getblocks/{hash}`

Returns canonical-chain hashes after the specified hash.

### `GET /getdata/{hash}`

Returns raw stored block data for a specific hash.

### `GET /transactions`

Returns hashes of pending transactions currently known to the node.

### `GET /transactions/{hash}`

Returns the stored transaction payload for a specific hash.

### `POST /inv`

Accepts either a legacy plain transaction or a structured signed transaction.

Legacy format:

```json
{
  "data": "alice->bob:5"
}
```

Structured format:

```json
{
  "signature": "base64-signature",
  "transaction": {
    "from": "base64-public-key",
    "to": "base64-public-key",
    "amount": 5.0,
    "timestamp": "2026-05-17T20:00:00Z"
  }
}
```

Structured validation covers:

- required fields
- signature correctness
- duplicate detection
- available balance on the canonical chain
- overspending against pending outgoing transactions

### `POST /block`

Accepts either a legacy plain block or a structured ledger block.

Legacy format:

```json
{
  "data": "block-001"
}
```

Structured format:

```json
{
  "height": 2,
  "previousHash": "previous-block-hash",
  "timestamp": "2026-05-17T20:00:00Z",
  "nonce": "1a2b",
  "hash": "computed-block-hash",
  "creator": "base64-public-key",
  "merkleRoot": "computed-merkle-root",
  "transactions": [
    {
      "hash": "transaction-hash",
      "signature": "base64-signature",
      "transaction": {
        "from": "base64-public-key",
        "to": "base64-public-key",
        "amount": 5.0,
        "timestamp": "2026-05-17T20:00:00Z"
      }
    }
  ]
}
```

Structured block validation covers:

- block hash consistency
- `height` / `previousHash`
- transaction signatures
- reward transaction rules
- Merkle root
- mining difficulty
- canonical fork-choice rules

Successful `POST /inv` and `POST /block` responses:

```json
{
  "accepted": true,
  "hash": "created-or-received-hash"
}
```

Typical error responses:

- `400` invalid or missing payload
- `405` wrong HTTP method
- `409` duplicate transaction or block

## Simulation

The simulation runner starts isolated node processes, executes network scenarios, and writes:

- logs to `build/simulation/logs/`
- markdown reports to `build/simulation/reports/`

Supported scenarios:

- `divergence`
- `convergence`
- `partition-failure`
- `propagation`
- `recovery`
- `scale`

Examples:

```powershell
.\gradlew.bat runSimulation --args="divergence"
.\gradlew.bat runSimulation --args="scale"
```

Latest local verification run on May 17, 2026:

- `.\gradlew.bat test` -> `BUILD SUCCESSFUL`
- `.\gradlew.bat runSimulation` -> all scenarios `PASS`
- full report: [network-simulation-report-20260517-203404.md](/C:/Users/Acer0/IdeaProjects/peer-ledger/build/simulation/reports/network-simulation-report-20260517-203404.md)

In the latest run, the scale scenario passed on `5`, `10`, `20`, and `30` nodes.
