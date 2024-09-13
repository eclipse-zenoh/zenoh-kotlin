# Zenoh Kotlin examples

----

## Start instructions



```bash
  gradle <example> --args="<arguments>"
```

for instance

```bash
  gradle ZPub --args="-h"
```

will return
```bash
> Task :examples:ZPub
Usage: zpub [<options>]

  Zenoh Pub example

Options:
  -k, --key=<key>             The key expression to write to [default:
                              demo/example/zenoh-kotlin-pub]
  -c, --config=<config>       A configuration file.
  -e, --connect=<connect>...  Endpoints to connect to.
  -l, --listen=<listen>...    Endpoints to listen on.
  -m, --mode=<mode>           The session mode. Default: peer. Possible values:
                              [peer, client, router]
  -v, --value=<value>         The value to write. [Default: "Pub from Kotlin!"]
  -a, --attach=<attach>       The attachments to add to each put. The key-value
                              pairs are &-separated, and = serves as the
                              separator between key and value.
  --no-multicast-scouting     Disable the multicast-based scouting mechanism.
  -h, --help                  Show this message and exit

```

The connect and listen parameters (that are common to all the examples) accept multiple repeated inputs.
For instance:

```bash
  gradle ZPub --args="-l tcp/localhost:7447 -l tcp/localhost:7448 -l tcp/localhost:7449"
```

There is the possibility to provide a Zenoh config file as follows
```bash
  gradle ZPub --args="-c path/to/config.json5"
```

In that case, any other provided configuration parameters through the command line interface will not be taken into consideration.

One last comment regarding Zenoh logging for the examples, remember it can be enabled through the environment variable `RUST_LOG` as follows:

```bash
  RUST_LOG=<level> gradle ZPub
```

where `<level>` can be either `info`, `trace`, `debug`, `warn` or `error`.

----

## Examples description

### ZPub

Declares a resource with a path and a publisher on this resource. Then puts a value using the numerical resource id.
The path/value will be received by all matching subscribers, for instance the [ZSub](#zsub) example.

Usage:

```bash
gradle ZPub
```
or
```bash
gradle ZPub --args="-k demo/example/test -v 'hello world'"
```

### ZSub
Creates a subscriber with a key expression.
The subscriber will be notified of each put made on any key expression matching
the subscriber's key expression, and will print this notification.

Usage:

```bash
gradle ZSub
```
or
```bash
gradle ZSub --args="-k demo/example/test"
```

### ZGet

Sends a query message for a selector.
The queryables with a matching path or selector (for instance [ZQueryable](#zqueryable))
will receive this query and reply with paths/values that will be received by the query callback.

```bash
gradle ZGet
```
or

```bash
gradle ZGet --args="-s demo/example/get"
```

### ZPut

Puts a path/value into Zenoh.
The path/value will be received by all matching subscribers, for instance the [ZSub](#zsub).

Usage:

```bash
gradle ZPut
```

or

```bash
gradle ZPut --args="-k demo/example/put -v 'Put from Kotlin!'"
```

### ZDelete
Performs a Delete operation into a path/value into Zenoh.

Usage:

```bash
gradle ZDelete
```

or

```bash
gradle ZDelete --args="-k demo/example/delete"
```

### ZQueryable

Creates a queryable function with a key expression.
This queryable function will be triggered by each call to a get operation on zenoh
with a selector that matches the key expression, and will return a value to the querier.

Usage:

```bash
gradle ZQueryable
```

or

```bash
gradle ZQueryable --args="-k demo/example/query"
```

### ZPubThr & ZSubThr

Pub/Sub throughput test.
This example allows to perform throughput measurements between a publisher performing
put operations and a subscriber receiving notifications of those puts.

Subscriber usage:

```bash
gradle ZSubThr
```

Publisher usage:

```bash
gradle ZPubThr <payload_size>
```
