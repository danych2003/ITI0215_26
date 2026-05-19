# Network Simulation Report

## Divergence Without Consensus Scenario

- Result: PASS
- Nodes: 3
- DurationMs: 930
- Background services disabled for all nodes
- Each node receives a different local transaction and block
- Broadcast is still enabled, so nodes learn the same data but may preserve different arrival order
- Canonical chain agreement: NO
- Transaction pool agreement: NO
- localhost:55366 -> blockHashes=[2208416c320dc41a197d0e2b7c813e9c392e6749b8575f3035be4445985fc382, 54c422a139a95772d050f00783b0f8dca8cbb69dfea187e57d7ae972721e2112, 4ccf339a6b140175b5ec4035a4ff868df072260f998e7429435695c9b0a38413], transactionHashes=[d3ef718a9f862ae339e4979883f06c45fff00c3b82f583f5be58483b66b2c42a, 7a8afa5d81a2a11145ca5bc0486fa23dd73d788bb5c9d03e4bbddd84823f2907, 5da5a151f38961cf9791b3548a5137df719fb18c861eeaa2c0e70e801da2896d]
- localhost:55367 -> blockHashes=[4ccf339a6b140175b5ec4035a4ff868df072260f998e7429435695c9b0a38413, 2208416c320dc41a197d0e2b7c813e9c392e6749b8575f3035be4445985fc382, 54c422a139a95772d050f00783b0f8dca8cbb69dfea187e57d7ae972721e2112], transactionHashes=[7a8afa5d81a2a11145ca5bc0486fa23dd73d788bb5c9d03e4bbddd84823f2907, d3ef718a9f862ae339e4979883f06c45fff00c3b82f583f5be58483b66b2c42a, 5da5a151f38961cf9791b3548a5137df719fb18c861eeaa2c0e70e801da2896d]
- localhost:55368 -> blockHashes=[2208416c320dc41a197d0e2b7c813e9c392e6749b8575f3035be4445985fc382, 54c422a139a95772d050f00783b0f8dca8cbb69dfea187e57d7ae972721e2112, 4ccf339a6b140175b5ec4035a4ff868df072260f998e7429435695c9b0a38413], transactionHashes=[d3ef718a9f862ae339e4979883f06c45fff00c3b82f583f5be58483b66b2c42a, 7a8afa5d81a2a11145ca5bc0486fa23dd73d788bb5c9d03e4bbddd84823f2907, 5da5a151f38961cf9791b3548a5137df719fb18c861eeaa2c0e70e801da2896d]
- localhost:55366 -> peers=3, blocks=3, transactions=3
- localhost:55367 -> peers=3, blocks=3, transactions=3
- localhost:55368 -> peers=3, blocks=3, transactions=3

## Convergence With Consensus Scenario

- Result: PASS
- Nodes: 3
- DurationMs: 1569
- Phase 1: isolated nodes accepted competing branches
- localhost:55369 -> blockHashes=[11d19caba4fa264a30ef61ce7d1ec4efa0ee8f9af52cb0d7f5fe6a57d28b784f, d5915b20f327a50bc1ae056abb4531b695a9de6a6079d25ee06a0ca12f3a7c7c], transactionHashes=[]
- localhost:55370 -> blockHashes=[11d19caba4fa264a30ef61ce7d1ec4efa0ee8f9af52cb0d7f5fe6a57d28b784f, 55fde144e6ba94ca96ca195db6144097dab765dbe989d1166f753e65c378495d], transactionHashes=[]
- localhost:55371 -> blockHashes=[11d19caba4fa264a30ef61ce7d1ec4efa0ee8f9af52cb0d7f5fe6a57d28b784f], transactionHashes=[]
- Phase 2: nodes restarted with shared peer set and background sync enabled
- Expected winning branch: 55fde144e6ba94ca96ca195db6144097dab765dbe989d1166f753e65c378495d
- localhost:55369 -> blockHashes=[11d19caba4fa264a30ef61ce7d1ec4efa0ee8f9af52cb0d7f5fe6a57d28b784f, 55fde144e6ba94ca96ca195db6144097dab765dbe989d1166f753e65c378495d], transactionHashes=[]
- localhost:55370 -> blockHashes=[11d19caba4fa264a30ef61ce7d1ec4efa0ee8f9af52cb0d7f5fe6a57d28b784f, 55fde144e6ba94ca96ca195db6144097dab765dbe989d1166f753e65c378495d], transactionHashes=[]
- localhost:55371 -> blockHashes=[11d19caba4fa264a30ef61ce7d1ec4efa0ee8f9af52cb0d7f5fe6a57d28b784f, 55fde144e6ba94ca96ca195db6144097dab765dbe989d1166f753e65c378495d], transactionHashes=[]
- localhost:55369 -> peers=3, blocks=2, transactions=0
- localhost:55370 -> peers=3, blocks=2, transactions=0
- localhost:55371 -> peers=3, blocks=2, transactions=0

## Consensus Failure Under Permanent Partition Scenario

- Result: PASS
- Nodes: 4
- DurationMs: 1164
- Consensus services enabled, but peer graph is permanently partitioned into 2 isolated groups
- Left partition expected branch: 8cc0082d03be95932d818e17253952d4c139fa5bfa32a06f406f1de2fd796be2
- Right partition expected branch: 031c52a95f9d3794a2e5750ce95af1d99e75430471f564681f177a97dcbbf24f
- Global canonical chain agreement: NO
- localhost:55372 -> blockHashes=[6ca26b729fa5fa80eaebd90d9991719f194422bcd62ba80fddf874fc172478e0, 8cc0082d03be95932d818e17253952d4c139fa5bfa32a06f406f1de2fd796be2], transactionHashes=[]
- localhost:55373 -> blockHashes=[6ca26b729fa5fa80eaebd90d9991719f194422bcd62ba80fddf874fc172478e0, 8cc0082d03be95932d818e17253952d4c139fa5bfa32a06f406f1de2fd796be2], transactionHashes=[]
- localhost:55374 -> blockHashes=[6ca26b729fa5fa80eaebd90d9991719f194422bcd62ba80fddf874fc172478e0, 031c52a95f9d3794a2e5750ce95af1d99e75430471f564681f177a97dcbbf24f], transactionHashes=[]
- localhost:55375 -> blockHashes=[6ca26b729fa5fa80eaebd90d9991719f194422bcd62ba80fddf874fc172478e0, 031c52a95f9d3794a2e5750ce95af1d99e75430471f564681f177a97dcbbf24f], transactionHashes=[]
- localhost:55372 -> peers=2, blocks=2, transactions=0
- localhost:55373 -> peers=2, blocks=2, transactions=0
- localhost:55374 -> peers=2, blocks=2, transactions=0
- localhost:55375 -> peers=2, blocks=2, transactions=0

## Propagation Scenario

- Result: PASS
- Nodes: 3
- DurationMs: 892
- Started nodes: localhost:55376, localhost:55377, localhost:55378
- Sent 1 transaction and 1 block to localhost:55376
- localhost:55376 -> peers=3, blocks=1, transactions=1
- localhost:55377 -> peers=3, blocks=1, transactions=1
- localhost:55378 -> peers=3, blocks=1, transactions=1

## Failure Recovery Scenario

- Result: PASS
- Nodes: 3
- DurationMs: 938
- Stopped node: localhost:55381
- Sent block while node was offline via localhost:55379
- Started nodes: localhost:55379, localhost:55380, localhost:55381
- localhost:55379 -> peers=3, blocks=1, transactions=0
- localhost:55380 -> peers=3, blocks=1, transactions=0
- localhost:55381 -> peers=3, blocks=1, transactions=0

## Scale Series Scenario

- Result: PASS
- Nodes: 30
- DurationMs: 15309
- Scale run 5 nodes: PASS, transactions=5, blocks=3, seed=localhost:55382
- localhost:55382 -> peers=5, blocks=3, transactions=5
- localhost:55383 -> peers=5, blocks=3, transactions=5
- localhost:55384 -> peers=5, blocks=3, transactions=5
- localhost:55385 -> peers=5, blocks=3, transactions=5
- localhost:55386 -> peers=5, blocks=3, transactions=5
- Scale run 10 nodes: PASS, transactions=10, blocks=5, seed=localhost:55387
- localhost:55387 -> peers=10, blocks=5, transactions=10
- localhost:55388 -> peers=10, blocks=5, transactions=10
- localhost:55389 -> peers=10, blocks=5, transactions=10
- localhost:55390 -> peers=10, blocks=5, transactions=10
- localhost:55391 -> peers=10, blocks=5, transactions=10
- localhost:55392 -> peers=10, blocks=5, transactions=10
- localhost:55393 -> peers=10, blocks=5, transactions=10
- localhost:55394 -> peers=10, blocks=5, transactions=10
- localhost:55395 -> peers=10, blocks=5, transactions=10
- localhost:55396 -> peers=10, blocks=5, transactions=10
- Scale run 20 nodes: PASS, transactions=20, blocks=10, seed=localhost:55397
- localhost:55397 -> peers=20, blocks=10, transactions=20
- localhost:55398 -> peers=20, blocks=10, transactions=20
- localhost:55399 -> peers=20, blocks=10, transactions=20
- localhost:55400 -> peers=20, blocks=10, transactions=20
- localhost:55401 -> peers=20, blocks=10, transactions=20
- localhost:55402 -> peers=20, blocks=10, transactions=20
- localhost:55403 -> peers=20, blocks=10, transactions=20
- localhost:55404 -> peers=20, blocks=10, transactions=20
- localhost:55405 -> peers=20, blocks=10, transactions=20
- localhost:55406 -> peers=20, blocks=10, transactions=20
- localhost:55407 -> peers=20, blocks=10, transactions=20
- localhost:55408 -> peers=20, blocks=10, transactions=20
- localhost:55409 -> peers=20, blocks=10, transactions=20
- localhost:55410 -> peers=20, blocks=10, transactions=20
- localhost:55411 -> peers=20, blocks=10, transactions=20
- localhost:55412 -> peers=20, blocks=10, transactions=20
- localhost:55413 -> peers=20, blocks=10, transactions=20
- localhost:55414 -> peers=20, blocks=10, transactions=20
- localhost:55415 -> peers=20, blocks=10, transactions=20
- localhost:55416 -> peers=20, blocks=10, transactions=20
- Scale run 30 nodes: PASS, transactions=30, blocks=15, seed=localhost:55417
- localhost:55417 -> peers=30, blocks=15, transactions=30
- localhost:55418 -> peers=30, blocks=15, transactions=30
- localhost:55419 -> peers=30, blocks=15, transactions=30
- localhost:55420 -> peers=30, blocks=15, transactions=30
- localhost:55421 -> peers=30, blocks=15, transactions=30
- localhost:55422 -> peers=30, blocks=15, transactions=30
- localhost:55423 -> peers=30, blocks=15, transactions=30
- localhost:55424 -> peers=30, blocks=15, transactions=30
- localhost:55425 -> peers=30, blocks=15, transactions=30
- localhost:55426 -> peers=30, blocks=15, transactions=30
- localhost:55427 -> peers=30, blocks=15, transactions=30
- localhost:55428 -> peers=30, blocks=15, transactions=30
- localhost:55429 -> peers=30, blocks=15, transactions=30
- localhost:55430 -> peers=30, blocks=15, transactions=30
- localhost:55431 -> peers=30, blocks=15, transactions=30
- localhost:55432 -> peers=30, blocks=15, transactions=30
- localhost:55433 -> peers=30, blocks=15, transactions=30
- localhost:55434 -> peers=30, blocks=15, transactions=30
- localhost:55435 -> peers=30, blocks=15, transactions=30
- localhost:55436 -> peers=30, blocks=15, transactions=30
- localhost:55437 -> peers=30, blocks=15, transactions=30
- localhost:55438 -> peers=30, blocks=15, transactions=30
- localhost:55439 -> peers=30, blocks=15, transactions=30
- localhost:55440 -> peers=30, blocks=15, transactions=30
- localhost:55441 -> peers=30, blocks=15, transactions=30
- localhost:55442 -> peers=30, blocks=15, transactions=30
- localhost:55443 -> peers=30, blocks=15, transactions=30
- localhost:55444 -> peers=30, blocks=15, transactions=30
- localhost:55445 -> peers=30, blocks=15, transactions=30
- localhost:55446 -> peers=30, blocks=15, transactions=30

