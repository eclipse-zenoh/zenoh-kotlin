# Zenoh Kotlin examples

----

## Start instructions



```bash
  ./gradle <example>
```

:warning: Passing arguments to these examples has not been enabled yet for this first version. Altering the Zenoh
configuration for these examples must be done programmatically. :warning:

---- 

## Examples description

### ZPub

Declares a resource with a path and a publisher on this resource. Then puts a value using the numerical resource id.
The path/value will be received by all matching subscribers, for instance the [ZSub](#zsub) example.

Usage:

```bash
./gradle ZPub
```

### ZSub
Creates a subscriber with a key expression.
The subscriber will be notified of each put made on any key expression matching
the subscriber's key expression, and will print this notification.

Usage:

```bash
./gradle ZSub
```

### ZGet

Sends a query message for a selector.
The queryables with a matching path or selector (for instance [ZQueryable](#zqueryable))
will receive this query and reply with paths/values that will be received by the query callback.

```bash
./gradle ZGet
```
    
### ZPut

Puts a path/value into Zenoh.
The path/value will be received by all matching subscribers, for instance the [ZSub](#zsub).

Usage:

```bash
./gradle ZPut
```

### ZDelete
Performs a Delete operation into a path/value into Zenoh.

Usage:

```bash
./gradle ZDelete
```

### ZQueryable

Creates a queryable function with a key expression.
This queryable function will be triggered by each call to a get operation on zenoh
with a selector that matches the key expression, and will return a value to the querier.

Usage:

```bash
./gradle ZQueryable
```

### ZPubThr & ZSubThr

Pub/Sub throughput test.
This example allows to perform throughput measurements between a publisher performing
put operations and a subscriber receiving notifications of those puts.

Subscriber usage:

```bash
./gradle ZSubThr
```

Publisher usage:

```bash
./gradle ZPubThr
```
