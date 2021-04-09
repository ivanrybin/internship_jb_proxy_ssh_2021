
### Kotlin client/server for Fibonacci number calculation written in Java-like style
 
Args format
```
--server --port 12345 --host localhost
--client --port 12345 --host localhost
```
 
Protocol

```
client -> server: 4 bytes == N      (Int)
server -> client: 8 bytes == fib(N) (Long)
```
 
Client usage example
```
10              // request
answer: 55      // response

(print enter to exit) // exit
```

Run
```
./run.sh --server --port 12345 --host localhost

./run.sh --client --port 12345 --host localhost
```
