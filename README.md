# Elko

### A server framework for stateful, sessionful applications in the web

Elko is a Java-based server framework for building stateful, sessionful
applications in the web.  It is especially suited to applications that require
realtime interaction among multiple clients, such as realtime text chat or
multiplayer games, or that have a strong "push" component wherein the server
needs to initiate much of the interaction, such as service monitors or realtime
auctions.

Much of the scalability of the web derives from the statelessness of the HTTP
protocol and the consequent ease with which load may be distributed by simply
replicating web servers. However, there remain a core of interesting and
valuable interactive network applications that are fundamentally stateful, and
trying to shoehorn these into the web paradigm can be awkward and frustrating.

Elko is an application server framework designed to address this, enabling you
to quickly and easily create applications that require a live, truly
bidirectional dialog between client and server. 

Elko is highly scalable and very performant.  We have successfully run
configurations supporting upwards of 150,000 concurrently connected real-time
users on a single AWS "large" server instance.

## General Information

This README describes Elko release 2.0.1, dated 23-February-2016.

The authoritative source and documentation for Elko is maintained at:

https://github.com/FUDCo/Elko (i.e., here)

or

http://elkoserver.org (which currently redirects to here)


Background and theory are discussed in a series of three [Habitat
Chronicles](http://habitatchronicles.com/) blog posts:

* [Part I: The Life, Death, Life, Death, Life, Death, and Resurrection of The
Elko Session
Server](http://habitatchronicles.com/2009/09/elko-i-the-life-death-life-death-life-death-and-resurrection-of-the-elko-session-sever/)

* [Part II: Against Statelessness (or, Everything Old Is New
Again)](http://habitatchronicles.com/2009/09/elko-ii-against-statelessness-or-everything-old-is-new-again/)

* [Part III: Scale
Differently](http://habitatchronicles.com/2009/09/elko-iii-scale-differently/)

Elko is open source software, under the MIT license.  See the file `LICENSE.md`

## What's Here

* `Build` -- contains top level build files; however, you probably want the
  ones in `ServerCore/build` instead (see the **Building** section immediately
  below).

* `ServerCore` -- contains the Elko server framework itself, along with its
  documentation.

* `Web` -- contains client-side JavaScript and HTML for interacting with Elko
  applications from a web browser.

* `Run` -- contains a variety of shell scripts for running and managing various
  server farm configurations, as well as the beginnings of a web-based
  adminstration console (written in PHP).

* `ZeroMQ` -- contains a pluggable extension that lets Elko servers talk to
  things using the [ZeroMQ](http://zeromq.org) distributed messaging framework.

#### Building

To build the Java code from the sources directly as is, you will also need the
[jdep](https://github.com/FUDCo/jdep) utility and GNU Make.

Note that most people doing Java development these days use one of the several
popular Java IDEs and/or [Maven](https://maven.apache.org), but at the moment
there's no support here for these; I'm an old time Unix/emacs guy and never had
much use for such newfangled contraptions (especially Maven, yuck). However,
the Java source tree is structured in the conventional way, so you should just
be able to import the source tree into your favorite IDE and press the build
button.

The `ServerCore` classes have only two external dependencies outside the normal
class libraries that are part of the standard JDK. These are the [MongoDB
client libaries](https://docs.mongodb.org/ecosystem/drivers/java/), if want
support for MongoDB-based object persistence (and which you can do without in a
pinch, as it is not strictly necessary for all use cases) and the [Apache
Commons Codec packages](https://commons.apache.org/proper/commons-codec/) (to
replace the now deprecated Sun base-64 encoder and decoder that Java
applications relied on for so long).

More detail on building will be presented in an accompanying `BUILD.md` file
once I get done writing it.

The current build requires Java 8 or newer. MongoDB persistence uses the
`mongodb-driver-legacy` 5.13.0 artifact and its matching `mongodb-driver-sync`,
`mongodb-driver-core`, and `bson` dependencies. This retains Elko's existing
storage API while supporting MongoDB 8. To run the standalone storage regression
against a running loopback MongoDB, use
`bash Build/test-mongo.sh /path/to/dependency-jars 27017`; the directory must also
contain Commons Codec 1.9. The test creates and drops its own uniquely named
database and does not migrate existing data. The various shell
scripts in the `Run` tree do assume a Unix shell environment, but Cygwin will
suffice and they are not deeply essential anyway.

For mixed deployments that need additional stored metadata, the Mongo store
supports two opt-in settings (shown with the Repository's property prefix):

* `conf.rep.odb.mongo.preservefields=_etnaOwnership` retains the explicitly
  listed underscore-prefixed fields during reads and queries. Use a comma-separated
  list for multiple fields. Other underscore fields remain filtered; `_id` and
  the generated `_qpos_` retain their existing special treatment and cannot be
  listed. The default preserves no additional fields.
* `conf.rep.odb.mongo.legacyversionzero=true` permits an atomic update expecting
  version zero to match either explicit zero or an absent version. It does not
  match a null version. The caller still supplies the replacement's next version.
  The default retains the original exact-version behavior.

Provision a unique `ref` index for every collection used with create-only writes.
These options do not add a version guard to physical removal. Etna ownership
deletion uses conditional replacement with a tombstone instead.

#### Binaries

If you don't feel like building Elko yourself, a pre-built .jar file is
available in the attached Release (v2.0.1).

#### Documentation

Detailed API and protocl documentation is sourced in the `ServerCore/doc`
directory, but is viewable directly [here](http://fudco.github.io/Elko/).  The
*formatting* of what's there right now is a bit rough, not having entirely made
the transition from its old home, but the content per se is as complete and up
to date as ever.  This will get prettier when I complete the migration to
Jekyll.

