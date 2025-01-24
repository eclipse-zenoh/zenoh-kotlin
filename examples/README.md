# Zenoh Kotlin examples

----

## Start instructions

For running the examples, there are two approaches. Either you run the gradle example task with the syntax `gradle <example> --args="<arguments>"` or you can build fat JARs the examples through the task `gradle buildExamples`.

While both are valid, in this document we'll follow the second approach; when running `gradle buildExamples`, all the JARs will be located under `/examples/build/libs`, which can later be executed

```bash
java -jar <Example>.jar <arguments>
```

for instance

```bash
java -jar ZPub.jar -h
```

will return

```bash
Usage: zpub [<options>]

  Zenoh Pub example

Options:
  -k, --key=<key>          The key expression to write to [default: demo/example/zenoh-kotlin-pub]
  -c, --config=<config>    A configuration file.
  -e, --connect=<connect>  Endpoints to connect to.
  -l, --listen=<listen>    Endpoints to listen on.
  -m, --mode=<mode>        The session mode. Default: peer. Possible values: [peer, client, router]
  -v, --value=<value>      The value to write. [Default: "Pub from Kotlin!"]
  -a, --attach=<attach>    The attachments to add to each put. The key-value pairs are &-separated, and = serves as the separator between key and value.
  --no-multicast-scouting  Disable the multicast-based scouting mechanism.
  -h, --help               Show this message and exit
```

The connect and listen parameters (that are common to all the examples) accept multiple repeated inputs.
For instance:

```bash
java -jar ZPub.jar -l tcp/localhost:7447 -l tcp/localhost:7448 -l tcp/localhost:7449
```

There is the possibility to provide a Zenoh config file as follows

```bash
java -jar ZPub.jar -c path/to/config.json5
```

In that case, any other provided configuration parameters through the command line interface will not be taken into consideration.

One last comment regarding Zenoh logging for the examples, logs from the native library can be enabled through the environment variable `RUST_LOG` as follows:

```bash
RUST_LOG=<level> java -jar ZPub.jar
```

where `<level>` is the log filter (for instance `debug`, `warn`, `error`... (see the [Rust documentation](https://docs.rs/env_logger/latest/env_logger/#enabling-logging))).

----

## Examples description

### ZPub

Declares a resource with a path and a publisher on this resource. Then puts a value using the numerical resource id.
The path/value will be received by all matching subscribers, for instance the [ZSub](#zsub) example.

Usage:

```bash
java -jar ZPub.jar
```

or

```bash
java -jar ZPub.jar -k demo/example/test -v "hello world"
```

### ZSub

Creates a subscriber with a key expression.
The subscriber will be notified of each put made on any key expression matching
the subscriber's key expression, and will print this notification.

Usage:

```bash
java -jar ZSub.jar
```

or

```bash
java -jar ZSub.jar -k demo/example/test
```

### ZGet

Sends a query message for a selector.
The queryables with a matching path or selector (for instance [ZQueryable](#zqueryable))
will receive this query and reply with paths/values that will be received by the query callback.

```bash
java -jar ZGet.jar
```

or

```bash
java -jar ZGet.jar -s demo/example/get
```

### ZPut

Puts a path/value into Zenoh.
The path/value will be received by all matching subscribers, for instance the [ZSub](#zsub).

Usage:

```bash
java -jar ZPut.jar
```

or

```bash
java -jar ZPut.jar -k demo/example/put -v 'Put from Kotlin!'
```

### ZDelete

Performs a Delete operation into a path/value into Zenoh.

Usage:

```bash
java -jar ZDelete.jar
```

or

```bash
java -jar ZDelete.jar -k demo/example/delete
```

### ZQueryable

Creates a queryable function with a key expression.
This queryable function will be triggered by each call to a get operation on zenoh
with a selector that matches the key expression, and will return a value to the querier.

Usage:

```bash
java -jar ZQueryable.jar
```

or

```bash
java -jar ZQueryable.jar -k demo/example/query
```

### ZPubThr & ZSubThr

Pub/Sub throughput test.
This example allows to perform throughput measurements between a publisher performing
put operations and a subscriber receiving notifications of those puts.

Subscriber usage:

```bash
java -jar ZSubThr.jar
```

Publisher usage:

```bash
java -jar ZPubThr.jar <payload_size>
```

### ZPing & ZPong

Latency tests

```bash
java -jar ZPing.jar
```

```bash
java -jar ZPong.jar
```

### ZScout

A scouting example. Will show information from other nodes in the Zenoh network.

```bash
java -jar ZScout.jar
```

### Liveliness examples

#### ZLiveliness

The ZLiveliness example, it just announces itself to the Zenoh network by default to the key expression `group1/zenoh-kotlin`.

Usage:

```bash
java -jar ZLiveliness
```

It can be used along with the following liveliness examples:

#### ZGetLiveliness

Gets the liveliness tokens, by default to `group1/zenoh-kotlin`.

Usage:

```bash
java -jar ZGetLiveliness
```

#### ZSubLiveliness

Subscribes to liveliness events:

```bash
java -jar ZSubLiveliness
```
