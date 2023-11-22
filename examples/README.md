# Examples

----

## Start instructions


```bash
./gradle <example>
```

:warning: Passing arguments to these examples has not been enabled yet for this first version. Altering the Zenoh
configuration for these examples must be done programmatically. :warning:

The examples are available for both Java and Kotlin versions. To chose one over the other, you need to specify the proper task. For instance,
to run the ZPub example with Kotlin run:

```bash
./gradle examples:kotlin:ZPub
```

while to run the same example with the Java version, run:

```bash
./gradle examples:java:ZPub
```


The Java examples use the Java compatible Zenoh-Kotlin library, 
so there are some differences inherent to the language.   

---- 

## Examples description

### ZPub

Declares a resource with a path and a publisher on this resource. Then puts a value using the numerical resource id.
The path/value will be received by all matching subscribers, for instance the [ZSub](#zsub) example.

Usage:

```bash
./gradle examples:kotlin:ZPub
```

### ZSub
Creates a subscriber with a key expression.
The subscriber will be notified of each put made on any key expression matching
the subscriber's key expression, and will print this notification.

Usage:

```bash
./gradle examples:kotlin:ZSub
```

### ZGet

Sends a query message for a selector.
The queryables with a matching path or selector (for instance [ZQueryable](#zqueryable))
will receive this query and reply with paths/values that will be received by the query callback.

```bash
./gradle examples:kotlin:ZGet
```
    
### ZPut

Puts a path/value into Zenoh.
The path/value will be received by all matching subscribers, for instance the [ZSub](#zsub).

Usage:

```bash
./gradle examples:kotlin:ZPut
```

### ZDelete
Performs a Delete operation into a path/value into Zenoh.

Usage:

```bash
./gradle examples:kotlin:ZDelete
```

### ZQueryable

Creates a queryable function with a key expression.
This queryable function will be triggered by each call to a get operation on zenoh
with a selector that matches the key expression, and will return a value to the querier.

Usage:

```bash
./gradle examples:kotlin:ZQueryable
```

### ZPubThr & ZSubThr

Pub/Sub throughput test.
This example allows to perform throughput measurements between a publisher performing
put operations and a subscriber receiving notifications of those puts.

Subscriber usage:

```bash
./gradle examples:kotlin:ZSubThr
```

Publisher usage:

```bash
./gradle examples:kotlin:ZPubThr
```
