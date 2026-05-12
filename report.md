# Network Simulation Report

## Divergence Without Consensus Scenario

- Result: PASS
- Nodes: 3
- DurationMs: 992
- Background services disabled for all nodes
- Each node receives a different local transaction and block
- Broadcast is still enabled, so nodes learn the same data but may preserve different arrival order
- Canonical chain agreement: NO
- Transaction pool agreement: NO
- localhost:58953 -> blockHashes=[2208416c320dc41a197d0e2b7c813e9c392e6749b8575f3035be4445985fc382, 4ccf339a6b140175b5ec4035a4ff868df072260f998e7429435695c9b0a38413, 54c422a139a95772d050f00783b0f8dca8cbb69dfea187e57d7ae972721e2112], transactionHashes=[d3ef718a9f862ae339e4979883f06c45fff00c3b82f583f5be58483b66b2c42a, 7a8afa5d81a2a11145ca5bc0486fa23dd73d788bb5c9d03e4bbddd84823f2907, 5da5a151f38961cf9791b3548a5137df719fb18c861eeaa2c0e70e801da2896d]
- localhost:58954 -> blockHashes=[2208416c320dc41a197d0e2b7c813e9c392e6749b8575f3035be4445985fc382, 4ccf339a6b140175b5ec4035a4ff868df072260f998e7429435695c9b0a38413, 54c422a139a95772d050f00783b0f8dca8cbb69dfea187e57d7ae972721e2112], transactionHashes=[d3ef718a9f862ae339e4979883f06c45fff00c3b82f583f5be58483b66b2c42a, 7a8afa5d81a2a11145ca5bc0486fa23dd73d788bb5c9d03e4bbddd84823f2907, 5da5a151f38961cf9791b3548a5137df719fb18c861eeaa2c0e70e801da2896d]
- localhost:58955 -> blockHashes=[2208416c320dc41a197d0e2b7c813e9c392e6749b8575f3035be4445985fc382, 54c422a139a95772d050f00783b0f8dca8cbb69dfea187e57d7ae972721e2112, 4ccf339a6b140175b5ec4035a4ff868df072260f998e7429435695c9b0a38413], transactionHashes=[7a8afa5d81a2a11145ca5bc0486fa23dd73d788bb5c9d03e4bbddd84823f2907, d3ef718a9f862ae339e4979883f06c45fff00c3b82f583f5be58483b66b2c42a, 5da5a151f38961cf9791b3548a5137df719fb18c861eeaa2c0e70e801da2896d]
- localhost:58953 -> peers=3, blocks=3, transactions=3
- localhost:58954 -> peers=3, blocks=3, transactions=3
- localhost:58955 -> peers=3, blocks=3, transactions=3

## Convergence With Consensus Scenario

- Result: PASS
- Nodes: 3
- DurationMs: 1687
- Phase 1: isolated nodes accepted competing branches
- localhost:58956 -> blockHashes=[6447e6a713386d4bdc9db9c28d7128aab1cca00a55700512f360a6e41a2cc7f9, 9a8f0ed7eaa81877d93de642a8b680c16a9e1e2b4557da5c200c1ae333779177], transactionHashes=[]
- localhost:58957 -> blockHashes=[6447e6a713386d4bdc9db9c28d7128aab1cca00a55700512f360a6e41a2cc7f9, 89efbd0880b05e4e67989bfce5837d2ccb791093d375f4fa56063af418a6b313], transactionHashes=[]
- localhost:58958 -> blockHashes=[6447e6a713386d4bdc9db9c28d7128aab1cca00a55700512f360a6e41a2cc7f9], transactionHashes=[]
- Phase 2: nodes restarted with shared peer set and background sync enabled
- Expected winning branch: 89efbd0880b05e4e67989bfce5837d2ccb791093d375f4fa56063af418a6b313
- localhost:58956 -> blockHashes=[6447e6a713386d4bdc9db9c28d7128aab1cca00a55700512f360a6e41a2cc7f9, 89efbd0880b05e4e67989bfce5837d2ccb791093d375f4fa56063af418a6b313], transactionHashes=[]
- localhost:58957 -> blockHashes=[6447e6a713386d4bdc9db9c28d7128aab1cca00a55700512f360a6e41a2cc7f9, 89efbd0880b05e4e67989bfce5837d2ccb791093d375f4fa56063af418a6b313], transactionHashes=[]
- localhost:58958 -> blockHashes=[6447e6a713386d4bdc9db9c28d7128aab1cca00a55700512f360a6e41a2cc7f9, 89efbd0880b05e4e67989bfce5837d2ccb791093d375f4fa56063af418a6b313], transactionHashes=[]
- localhost:58956 -> peers=3, blocks=2, transactions=0
- localhost:58957 -> peers=3, blocks=2, transactions=0
- localhost:58958 -> peers=3, blocks=2, transactions=0

## Consensus Failure Under Permanent Partition Scenario

- Result: PASS
- Nodes: 4
- DurationMs: 1182
- Consensus services enabled, but peer graph is permanently partitioned into 2 isolated groups
- Left partition expected branch: 42504c9991d2735049ce2acf7c63b5777724abc6c4e5ff876e2c317b563f6ac9
- Right partition expected branch: 8d60d1c43547b133639bb48872260e03de14691fdf1de9ae123b90a17e063e6a
- Global canonical chain agreement: NO
- localhost:58959 -> blockHashes=[0ab40bc569a4671e2df2fa3aae5f6e0a5ab38aba23422c7e4b1130626c9339c1, 42504c9991d2735049ce2acf7c63b5777724abc6c4e5ff876e2c317b563f6ac9], transactionHashes=[]
- localhost:58960 -> blockHashes=[0ab40bc569a4671e2df2fa3aae5f6e0a5ab38aba23422c7e4b1130626c9339c1, 42504c9991d2735049ce2acf7c63b5777724abc6c4e5ff876e2c317b563f6ac9], transactionHashes=[]
- localhost:58961 -> blockHashes=[0ab40bc569a4671e2df2fa3aae5f6e0a5ab38aba23422c7e4b1130626c9339c1, 8d60d1c43547b133639bb48872260e03de14691fdf1de9ae123b90a17e063e6a], transactionHashes=[]
- localhost:58962 -> blockHashes=[0ab40bc569a4671e2df2fa3aae5f6e0a5ab38aba23422c7e4b1130626c9339c1, 8d60d1c43547b133639bb48872260e03de14691fdf1de9ae123b90a17e063e6a], transactionHashes=[]
- localhost:58959 -> peers=2, blocks=2, transactions=0
- localhost:58960 -> peers=2, blocks=2, transactions=0
- localhost:58961 -> peers=2, blocks=2, transactions=0
- localhost:58962 -> peers=2, blocks=2, transactions=0

## Propagation Scenario

- Result: PASS
- Nodes: 3
- DurationMs: 904
- Started nodes: localhost:58963, localhost:58964, localhost:58965
- Sent 1 transaction and 1 block to localhost:58963
- localhost:58963 -> peers=3, blocks=1, transactions=1
- localhost:58964 -> peers=3, blocks=1, transactions=1
- localhost:58965 -> peers=3, blocks=1, transactions=1

## Failure Recovery Scenario

- Result: PASS
- Nodes: 3
- DurationMs: 1110
- Stopped node: localhost:58968
- Sent block while node was offline via localhost:58966
- Started nodes: localhost:58966, localhost:58967, localhost:58968
- localhost:58966 -> peers=3, blocks=1, transactions=0
- localhost:58967 -> peers=3, blocks=1, transactions=0
- localhost:58968 -> peers=3, blocks=1, transactions=0

## Scale Series Scenario

- Result: FAIL
- Nodes: 20
- DurationMs: 38858
- Scale run 5 nodes: PASS, transactions=5, blocks=3, seed=localhost:58969
- localhost:58969 -> peers=5, blocks=3, transactions=5
- localhost:58970 -> peers=5, blocks=3, transactions=5
- localhost:58971 -> peers=5, blocks=3, transactions=5
- localhost:58972 -> peers=5, blocks=3, transactions=5
- localhost:58973 -> peers=5, blocks=3, transactions=5
- Scale run 10 nodes: PASS, transactions=10, blocks=5, seed=localhost:58974
- localhost:58974 -> peers=10, blocks=5, transactions=10
- localhost:58975 -> peers=10, blocks=5, transactions=10
- localhost:58976 -> peers=10, blocks=5, transactions=10
- localhost:58977 -> peers=10, blocks=5, transactions=10
- localhost:58978 -> peers=10, blocks=5, transactions=10
- localhost:58979 -> peers=10, blocks=5, transactions=10
- localhost:58980 -> peers=10, blocks=5, transactions=10
- localhost:58981 -> peers=10, blocks=5, transactions=10
- localhost:58982 -> peers=10, blocks=5, transactions=10
- localhost:58983 -> peers=10, blocks=5, transactions=10
- Scale run 20 nodes: PASS, transactions=20, blocks=10, seed=localhost:58984
- localhost:58984 -> peers=20, blocks=10, transactions=20
- localhost:58985 -> peers=20, blocks=10, transactions=20
- localhost:58986 -> peers=20, blocks=10, transactions=20
- localhost:58987 -> peers=20, blocks=10, transactions=20
- localhost:58988 -> peers=20, blocks=10, transactions=20
- localhost:58989 -> peers=20, blocks=10, transactions=20
- localhost:58990 -> peers=20, blocks=10, transactions=20
- localhost:58991 -> peers=20, blocks=10, transactions=20
- localhost:58992 -> peers=20, blocks=10, transactions=20
- localhost:58993 -> peers=20, blocks=10, transactions=20
- localhost:58994 -> peers=20, blocks=10, transactions=20
- localhost:58995 -> peers=20, blocks=10, transactions=20
- localhost:58996 -> peers=20, blocks=10, transactions=20
- localhost:58997 -> peers=20, blocks=10, transactions=20
- localhost:58998 -> peers=20, blocks=10, transactions=20
- localhost:58999 -> peers=20, blocks=10, transactions=20
- localhost:59000 -> peers=20, blocks=10, transactions=20
- localhost:59001 -> peers=20, blocks=10, transactions=20
- localhost:59002 -> peers=20, blocks=10, transactions=20
- localhost:59003 -> peers=20, blocks=10, transactions=20
- Scale run 30 nodes failed: all 30 nodes receive scale scenario messages failed: Address already in use: getsockopt
- localhost:59004 -> peers=30, blocks=15, transactions=30
- localhost:59005 -> peers=30, blocks=15, transactions=30
- localhost:59006 -> unreachable
- localhost:59007 -> unreachable
- localhost:59008 -> unreachable
- localhost:59009 -> unreachable
- localhost:59010 -> unreachable
- localhost:59011 -> unreachable
- localhost:59012 -> unreachable
- localhost:59013 -> unreachable
- localhost:59014 -> unreachable
- localhost:59015 -> unreachable
- localhost:59016 -> unreachable
- localhost:59017 -> unreachable
- localhost:59018 -> unreachable
- localhost:59019 -> unreachable
- localhost:59020 -> unreachable
- localhost:59021 -> unreachable
- localhost:59022 -> unreachable
- localhost:59023 -> unreachable
- localhost:59024 -> unreachable
- localhost:59025 -> unreachable
- localhost:59026 -> unreachable
- localhost:59027 -> unreachable
- localhost:59028 -> unreachable
- localhost:59029 -> unreachable
- localhost:59030 -> unreachable
- localhost:59031 -> unreachable
- localhost:59032 -> unreachable
- localhost:59033 -> unreachable

