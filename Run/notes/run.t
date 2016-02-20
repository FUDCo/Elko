Cluster: a group of servers configured to work together, under the control and
  administration of a common broker, with coordinated host and port allocation.

Cluster name: a simple alphanumeric identifier string that labels a configured
  cluster.

Component: one of the functionally specific servers in a cluster (e.g., broker,
  context server, director, etc.).  There may be multiple instances of any
  particular function (e.g., multiple context servers) for scale or redundancy;
  each such instance is considered a separate component.

Run setting: a per-component state that indicates whether the component is
  currently supposed to be running or not.  Its value is either "ON" or "OFF".

Run condition: a per-component state corresponding to whether the component is
  currently actually running or not.  The two primary values are "RUNNING" or
  "STOPPED", though we will sometimes also speak of the states "STARTING" or
  "STOPPING" to describe intervals when the component is in the transition
  between one of the two primary states and the other.  When a component is
  STARTING or STOPPING, its "true state" is considered to be unknown, but its
  previous state is known and we have expectations about its future state.
  After a time these latter expectations will either be fulfilled or violated.

Launcher: a script that may be executed either by a broker or from the shell
  that will cause a component to be put into the RUNNING condition by launching
  a new JVM process on some machine.

Component name: a string by which a component is labeled, for control and
  administration purposes, independent of its run setting or run condition.
  Typically, a component is named by giving its functional label (e.g.,
  "broker", "director", "context", etc.) optionally suffixed with an instance
  identifier to distinguish multiple components of the same type.  Instance
  identifiers may take the form of simple ordinals (as in "director-1",
  "director-2", etc.) or may follow any other labeling conventions we find
  convenient.  In particular they could be meaningful ("context-chipdev") or
  arbitrary ("context-cf706bc5") depending on our whim, but any embedded
  meaning is an administrative convention and is not expected to be understood
  by the software.  However, within a cluster, every component name must be
  unique.

Module: a collection of the pieces that make up a game or application that runs
  in our framework.  A module consists of a JAR file, possibly a set of class
  tag (represented by an ODB "classes" object), possibly a set of static
  objects (represented by an ODB "statics" object, plus descriptors for
  the static objects themselves), and possibly a collection of contexts, users,
  and items defined for the game.  By convention a module has a name, a short
  alphanumeric identifier string.  If the module is named "foo", the JAR file
  is "foo.jar", the classes object has the ref "classes-foo", and the statics
  object has the ref "statics-foo".

Run control:

The run setting represents the desired situation whereas the run condition
reflects the actual situation.

A component whose run setting is ON should be RUNNING.  A component whose run
setting is OFF should be STOPPED.  These pairings of states are considered
normal.  A component is considered to be in an abnormal state if its run
setting is OFF but it is RUNNING anyway, or if its run setting is ON but it is
STOPPED.  In other words, "normal" is when desire and reality match, and
"abnormal" is when they don't.

The purpose of the run control system is to maintain normal states (mainly by
managing the orderly transition betwixt ON/RUNNING and OFF/STOPPED) and recover
from abnormal states by converting them into normal states.  This is one of the
jobs of the broker, so much of what follows will describe the broker's
function.  However, a complication is that the broker can only handle this
management job when the broker itself is RUNNING, meaning that control over the
broker's state when it is STOPPED depends on external entities.

Note that a change in a component's run setting is a change to the broker's
state (another way of saying this is that the component run settings belong to
the broker rather than to the components themselves).  Normally such a change
is an act of the broker, but it could, in principle, be effected from outside,
especially if the broker is stopped and an alteration is made directly to the
persistently stored representation of the broker's state.  In contrast, a
change in a component's run condition is act of the component itself (albeit
one that may be the consequence of a broker action).

A component can go from the STOPPED condition to the RUNNING condition by being
launched via a launcher.  This can be happen properly via the broker, or
improperly via some kind of external action such as somebody typing in the
relevant shell command.

A component can go from the RUNNING condition to the STOPPED condition by being
commanded to shutdown or by crashing.  The software is sufficiently reliable
that components rarely (if ever) crash on their own.  However, the JVM process
that a component is running in can be killed by outside action, or the machine
on which the JVM process is running can crash due to hardware failure, VM
failure, or machine or OS or VM shutdown.  All of these latter termination
modes are indistinguishable from a component crash from the perspective of
other components and so all kinds of unexpected Stops are treated as a single
kind of event.

With respect to any particular component (besides the broker), we can consider
the permutations of possible component states, yielding 4 combination states:

State        Run         Run
Number     Setting    Condition
------    ---------   ---------
  1          ON        RUNNING
  2          OFF       RUNNING
  3          ON        STOPPED
  4          OFF       STOPPED

For the moment we'll assume the broker is always in a normal ON/RUNNING state.
There are considerations when it is in an abnormal state or OFF, but for these
the component state is irrelevant, so we'll address them separately.

State 1: Component ON/RUNNING

  This is the normal run state for a component.  Absent one of the following
  two events, the broker does nothing.

  Event: Shutdown Directive (via broker)
    The broker changes the component run setting to OFF and directs the
    component to shutdown, causing the component to transition to run condition
    STOPPED (state 4).  During the transition, the component run condition is
    STOPPING, which may briefly and falsely appear to be state 2.

  Event: Component Crash (external to broker)
    The component run setting remains ON and its run condition goes to STOPPED
    (state 3).

State 2: Component OFF/RUNNING

  This is an abnormal state and should never happen.  The principal failure
  mode that can to lead to this state is a component server stall during
  shutdown.

  If the broker detects this state, it should issue a directive to the
  component to shut down and transition to state 4.  If the state persists, the
  broker should invoke an external mechanism to kill the component process by
  force (also leading to state 4).

  Note that the illusion of being in this state may appear briefly as an
  ambiguous intermediate state between state 1 and state 4 when the component
  is actually STOPPING, and the broker should allow for this by waiting a
  reasonable time for the component to exit before concluding that shutdown has
  failed and intervening.

State 3: Component ON/STOPPED

  This is an abnormal state and should never happen.  This state indicates a
  component server crash.

  If the broker detects this state, it should attempt to (re)launch the
  component.  This may optionally also include the invocation of fault recovery
  and/or diagnostic operations prior to or as part of component startup.

  Note that the illusion of being in this state may appear briefly as an
  ambiguous intermediate state between state 4 and state 1 when the component
  is actually STARTING, and the broker should allow for this by waiting a
  reasonable time for the component to begin running before concluding that
  launch has failed and trying again.  Further, it is possible that an
  erroneous relaunch during a successful but slow component startup will result
  in two component instances being started.  In this case, the later instance
  should be directed to exit.

State 4: Component OFF/STOPPED

  This is the normal halted state for a component.  Absent one of the following
  two events, the broker does nothing.

  Event: Launch Directive (via broker)
    The broker changes the component run setting to ON and launches the
    component, causing it to transition to run condition RUNNING (state 1).
    During the transition, the component run condition is STARTING, which may
    briefly and falsely appear to be state 3.

  Event: Improper Launch (erroneous restart or launch external to broker)
    The component run setting remains OFF and its run condition goes to RUNNING
    (state 2).

In order to realize the state model just described, the broker needs to be able
to monitor component run states while itself RUNNING and to determine component
run states when STARTING.

The normal way that a broker monitors the run condition of a component is via
the connection that the component maintains to the broker when the component is
running.  One of the first things any component server will do on startup is
contact the broker.  If it cannot reach the broker, because of network
connectivity problems or because the broker is not running, it will keep
trying, periodically attempting to establish the connection, until it either
succeeds or its own process is terminated.  Once a broker connection is
established, if it is later lost (due, again, either to network connectivity
problems or broker stoppage), the component server will immediately start
trying to make a new broker connection in exactly the same manner as it did at
startup.  In the general case, it is not practical, without considerable added
engineering, for the broker to monitor the component server process directly
through the OS, as it is likely that the component will be running on a
different computer at the other end of a network connection anyway.
Consequently, rather than striving for perfect handling of all possible failure
modes, the broker will treat the presence or absence of a network connection
from a component as a proxy for the belief that the component is RUNNING or
STOPPED.  We then temper this epistemology with suitable timeouts to cover
expected transitions between the run conditions of both the components and the
broker itself.

Broker startup happens in one of three modes: cluster startup, broker restart,
and crash recovery.  In cluster startup mode, all component servers are assumed
to be STOPPED and their run settings are interpreted as the desired initial run
configuration.  In this mode, the broker should launch those components whose
run setting is ON without first taking time to check if they are already
running.  Broker restart mode and crash recovery mode are similar to each
other, differing in their expectations about the prior states of the
components.  In broker restart mode, all component servers are expected to be
in normal states, whereas in crash recovery mode nothing is known about the
components' states.  In both cases, subsequent connections to the broker by any
running components will allow the broker to decide which components are in
abnormal states that require remedial action.  The functional difference
between these two startup modes is simply one of expectation, which may be
reflected in different timeout configurations and differences in concurrency
aggressiveness when launching stopped components. In broker restart mode the
emphasis is on speed of return to normal operation, whereas in crash recovery
mode the emphasis is on restoration of a correct set of run conditions,
possibly at the cost of taking additional time to ensure that everything is
operating as it should.

When considering the run states of the broker itself, the same cases apply as
they do for the component servers, but the analysis is slightly different
because the broker, by definition, doesn't have a broker watching over it.  In
particular, states 2, 3, and 4 require action to be taken by some kind of
external entity.  We refer to this entity as the "external control framework",
but that could be anything with the appropriate authority, from the PHP cluster
management web app to a human manually issuing shell commands to the server
machine over an SSH connection.

In addition to tracking a run setting for itself, the broker and the external
control framework also manage a run setting for the cluster as a whole.  When
this cluster run setting is OFF, the broker and component run settings are
treated as if they were also OFF, but their actual settings are maintained
unchanged.  When the cluster run setting is ON, the individual run settings
govern.  In this way, we can shutdown a running cluster and then bring it back
up again later in the same run configuration.

State 1: Broker ON/RUNNING

  This is the normal run state for a broker.  Absent one of the following
  two events, the broker simply processes component and administration events
  normally.

  Event: Shutdown Directive (via an admin channel)
    The broker changes its run setting to OFF and exits, causing it to
    transition to run condition STOPPED (state 4).  During the transition, the
    broker run condition is STOPPING, which may briefly and falsely appear to
    be state 2.

  Event: Broker Crash (externally caused)
    The broker run setting remains ON and its run condition goes to STOPPED
    (state 3).

  The broker shutdown directive includes a flag indicating whether the cluster
  as a whole is being shutdown or just the broker.  If the cluster is being
  shutdown, the component servers are first directed to also shutdown, but
  their run settings are left unchanged.  The cluster run setting, however, is
  set to OFF.  If the cluster is not being shutdown, the component servers are
  not involved, and the cluster run setting is left ON.  A broker-only shutdown
  is atypical but it *is* supported.  For example, a broker might be stopped
  briefly to allow for a software upgrade.

State 2: Broker OFF/RUNNING

  This is an abnormal state and should never happen.  The principal failure
  modes that can to lead to this state are (1) a stall during broker shutdown
  and (2) improper behavior by an external actor who changes the broker run
  setting to OFF when it's not supposed to.  Note that in the first case the
  proper action would be to intervene externally and forcibly kill the broker
  process, whereas in the second case the proper action would be for the broker
  to correct its run setting.  However, there is no principled basis for being
  able to distinguish between these two cases; in particular, one could imagine
  a bug that manifested in the broker repeatedly and persistently turning its
  run setting back on instead of exiting when told.  Consequently, we have to
  follow a simpler rule.  Since we regard a runaway server as the more
  problematic condition, we will treat all occurances of state 2 as shutdown
  failures.  It is the job of the external control framework to force the
  broker to state 4 in this case.

  Note that the illusion of being in this state may appear briefly as an
  ambiguous intermediate state between state 1 and state 4 when the broker is
  actually STOPPING, and the external control framework should allow for this
  by waiting a reasonable time for the broker to exit before concluding that
  shutdown has failed and intervening.

State 3: Broker ON/STOPPED

  This is an abnormal state and should never happen.  This state indicates that
  the broker has crashed.

  If the external control framework detects this state, it should attempt to
  (re)launch the broker, pushing it to state 1 by launching it in crash
  recovery mode.

  Note that the illusion of being in this state may appear briefly as an
  ambiguous intermediate state between state 4 and state 1 when the broker is
  actually STARTING, and the control framework should allow for this by waiting
  a reasonable time for the broker to begin running before concluding that
  launch has failed and trying again.

State 4: Broker OFF/STOPPED

  This is the normal halted state for a broker.  Absent the following
  events, nothing happens.

  Event: Broker Launch
    The external control framework changes the broker run setting to ON and
    launches the broker, causing it to transition to run condition RUNNING
    (state 1).  During the transition, the broker run condition is STARTING,
    which may briefly and falsely appear to be state 3.  If the cluster run
    setting is ON, the broker should be started in broker restart mode.  If the
    cluster run setting is OFF, it should be set to ON and the broker started
    in cluster startup mode.

Run directory structure:

All servers run from beneath the Run directory, conventionally located in the
source tree at .../trunk/Server/Run

The Run directory contains several subdirectories:

common/          -- scripts shared in common by all runtime environments
manage/          -- PHP website for the Server Cluster Manager web app
mongosupport/    -- support files for our use of MongoDB
notes/           -- various cribsheets and bits of documentation
run.*/           -- runtime environments for the various cluster configurations
                    There can be any number of these.  For cluster "foo", the
                    runtime environment is in the directory "run.foo"
run_template/    -- template cluster configuration for generating new clusters


Shared scripts in the 'common' directory:

brokercmd       -- feeds admin commands to a running broker via netcat
brokerwatcher   -- watchdog script, suitable for execution at OS startup (via
   an entry in the /etc/init.d directory, for example), from which it will also
   start all suitably configured server clusters that should be running when
   the OS boots.  Watches the clusters that are configured to be running to
   make sure their brokers are live and restarts these brokers if they crash.
   It also can be signalled by the Server Cluster Manager web app to launch
   stopped clusters (this is a hack to get around the fact that the web app's
   PHP files execute in the Apache process with its user identity and
   associated permissions).
clusterStart    -- script to start a stopped cluster
clusterStop     -- script to stop shutdown a running cluster
kqwait.c        -- a utility for BSD-derived Unixes (Mac OSX, Solaris) to
   efficiently watch for changes in control directories using the OS kqueue
   API.  Linux systems do the same job via the open source inotifywait
   utility.  Used by brokerwatcher.
setenvvars      -- universal environment variable settings for all servers
setlocvars      -- (optional) env var settings peculiar to the current machine
setlocvars.*    -- (optional) env var settings peculiar to a particular user;
   for a user "foo", their settings, if they have some, are in "setlocvars.foo"
startComponent  -- script to start an individual component server
validateModule  -- script to validate an uploaded JAR file; currently just a
   placeholder


Noteworthy stuff in the 'manage' directory:

manage.php          -- PHP script for the Server Cluster Manager web app.
manage_environ.php  -- Assigns various locally configurable variables.
<other files>       -- CSS and images for the web app


Cluster run directory (the various run.* dirs):

control/            -- control semaphores for regulating run state; don't touch
launch.*            -- launch scripts for the various component servers.  For a
                       component "foo", the launch script is "launch.foo".  A
                       typical cluster will have components "broker",
                       "context", "director", and "workshop".
localmodules/       -- where the JAR files for installed modules reside
logs/               -- where log files go at runtime
modules.properties  -- Java properties file that informs the context server
                       about the installed modules; this is synthesized by the
                       Cluster Manager when modules are added or removed.
odb/                -- Simple flat-file ODB for the broker's config data
  classes.json                -- minimal classes table for a well-formed ODB
  launchertable.json          -- server launch scripts the broker can execute
  launchertable.json.backup   -- backup of above, in case it gets messed up
setlocvars          -- cluster-specific env var settings; don't touch
setlocvars.*        -- per-user cluster-specific env var settings
uploads/            -- staging directory for uploaded JARs

At run time, a few additional files will appear.  You can delete these if you
are purging a stopped cluster runtime of crud (the same time you would also be
clearing the control/ and logs/ directories), but don't mess with the contents
of these files:

broker.runparams    -- information about the host and port of the broker
broker.startmode    -- persistent start mode for the broker; used in restart


Port assignment:

A server can be configured with any number of listeners, each of which
corresponds to a way to connect to the server.  Each listener is characterized
by properties, defined on the server startup command line or in a properties
file, that describe four key configuration attributes:

   Address -- The contact information required to actually connect to the
   server via this listener: IP address or hostname, port number, and possibly
   additional information depending on the other attributes (for example,
   connection via HTTP also requires a URI).

   Transport protocol -- The protocol that will be used to carry our JSON
   messages: tcp, rtcp, http, zmq, etc.

   Protocol configuration -- Some transport protocols also require or allow
   additional dimensions of configuration.  For example, HTTP and RTCP
   connections have a variety of different kinds of timeouts that may be tuned.

   Access control -- The introductory message handshake that must be engaged in
   in order to gain access to the server via the listener.  For example, this
   can be open access, or may require a key, or an ID+password pair, or some
   other scheme.

   Role -- The particular kind of activity that entities connecting to the
   listern are allowed and expected to engage in.  For example a server might
   distinguish between admin vs. client connections.  In some cases, a single
   listener can support multiple roles.  The set of available roles varies
   depending on the type of server:

       broker
          admin    - system administration control messages
          client   - other servers in the cluster
       context
          internal - internally sourced inbound connections
          <client> - user clients
       director
          admin    - system administration control messages
          user     - user clients (to request reservations)
          provider - context servers
       gatekeeper
          admin    - system administration control messages
          user     - user clients (to request access)
       presence
          admin    - system administration control messages
          client   - other servers in the cluster
       repository
          admin    - system administration control messages
          rep      - other servers in the cluster
       workshop
          admin    - system administration control messages
          workshop - other servers in the cluster

Each listener listens for its configured type of connections on some port.  A
listener may be configured with an unassigned port number, in which case it
will have its port number allocated by the operating system from the pool of
free ports.  However, each listener has a corresponding port that gets assigned
to it somehow.

Because of the variety of different servers that might be running on a single
machine, combined with the variety of possible transport protocols and service
roles, a given cluster may have quite a complicated configuration.  In
particular, the allocation of port numbers to all the different uses is a
potentially challenging namespace management problem.

Automatically assigned ports are preferred where possible, as this leaves fewer
things to be managed manually (and therefor fewer things that can be
configured incorrectly by mistake).  The port number will be known once the
listener is actually initialized and then the server can communicate that port
number to the broker to be available to anyone who asks.

However, a port must be assigned explicitly if it needs to be known a priori
outside its server.  Examples of such ports are the broker's own ports (since
you can't ask the broker for another server's address until you can connect to
the broker in the first place) and the director's reservation ports (whose port
numbers may need to be hard-coded in user clients).  Furthermore, explicit
assignment of all ports may be desirable in test configurations, both so that a
precise configuration is repeatable and so that test scripts that interact with
all the various servers may do so without requiring secondary interactions with
the broker (such secondary interactions would themselves impede test
repeatability, plus they would considerably complicate the testing framework
for little if any added benefit).

Moreover, we may (particularly when debugging and testing) choose to run
multiple clusters on a given machine at the same time, and we need to make sure
they don't step on each other's port assignments.

Although we configure a server's listeners along several different dimensions,
the only two of these dimensions that are actually important for port
allocation are transport protocol and service role.  Access control settings
will be bound up with the server's policy for a given pairing of role and
protocol, and the remaining dimensions of listener configuration should not be
relevant to port assignment at all.  A third relevant dimension is the server's
role within the cluster (e.g., broker, director, etc.)

We could allocate port numbers with an encoding scheme that incorporates all
the permutations, say two bits for the protocol, four bits for the server type,
etc., but this would yield a fairly sparse allocation that would require us to
allocate huge blocks of possible port numbers per cluster.  Instead, I'm
proposing a scheme with a few more idiosyncratic special cases but much denser
port utilization, while still handling the common cases in an objective,
predictable way.  This scheme is a slightly formalized elaboration of the
loose pattern we've already been following.

The port allocation scheme is as follows: each cluster has a defined PORT_BASE
from which all the ports belonging to that server are computed arithmetically.
Each cluster gets a block of 100 port numbers from PORT_BASE to PORT_BASE+99.
Within the block of 100, we allocate 10 sub-blocks of 10 ports, each one
corresponding to a particular server in the cluster:

    00 = context server #1
    10 = broker 
    20 = workshop
    30 = gatekeeper
    40 = presence
    50 = repository
    60 = director
    70 = context server #2
    80 = context server #3
    90 = director #2 or context server #4

If new kinds of servers are defined, we will rearrange as needed.  Most
clusters don't have all (or even most) of these anyway.

We assign the 10 ports within a server's sub-block to various combinations of
role and protocol:

    0 = tcp first client role
    1 = http first client role
    2 = rtcp first client role
    3 = tcp second client role
    4 = http second client role
    5 = rtcp second client role
    6 = tcp admin
    7 = http admin
    8 = rtcp admin
    9 = other, as needed

Most server types have a single kind of client; in these cases this is the
first client role and there is no second client role.  The exception is the
director; in this case the user is considered the first client role and the
provider is considered the second client role.

In terms of the different server types, these break down into the following
standard port assignments:

  Context:
    0 = tcp user, reservation access*
    1 = http user, reservation access*
    2 = rtcp user, reservation access*
    3 = tcp user, open access*
    4 = http user, open access*
    5 = rtcp user, open access*
    6 = tcp internal, admin or open access*
    9 = zmq inbound internal, admin or open access*
    7,8 = <not used>

  Broker:
    0 = tcp client, intra or open access
    6 = tcp admin, admin or open access <any = client+admin, also allowed>
    7 = http admin, admin or open access
    1,2,3,4,5,8,9 = <not used>

  Workshop:
    0 = tcp workshop, intra or open access
    6 = tcp admin, admin or open access <any = client+admin, also allowed>
    7 = http admin, admin or open access
    1,2,3,4,5,8,9 = <not used>

  Gatekeeper:
    0 = tcp user, open access*
    1 = http user, open access*
    2 = rtcp user, open access*
    6 = tcp admin, admin or open access <any = client+admin, also allowed>
    7 = http admin, admin or open access
    3,4,5,8,9 = <not used>

  Presence:
    0 = tcp client, intra or open access
    6 = tcp admin, admin or open access <any = client+admin, also allowed>
    7 = http admin, admin or open access
    1,2,3,4,5,8,9 = <not used>

  Repository:
    0 = tcp rep, intra or open access
    6 = tcp admin, admin or open access <any = rep+admin, also allowed>
    7 = http admin, admin or open access
    1,2,3,4,5,8,9 = <not used>

  Director:
    0 = tcp user, open access*
    1 = http user, open access*
    2 = rtcp user, open access*
    3 = tcp provider, intra or open access
    6 = tcp admin, admin or open access <any = user+provider+admin, also allowed>
    7 = http admin, admin or open access
    9 = tcp user, intra access*
    4,5,8 = <not used>

Port assignments marked * can be enabled/disabled by customer configuration

Access 'intra' is secured using the intra-data-center cluster password.  This
password is mutually shared by all servers in a cluster that need to
interconnect.

Access 'admin' is secured using the cluster admin password.  This password is
known to human administrators and globally settable via its own interface.

On a personal development machine, port block assignment is the developer's own
business (on personal machine, most likely you'll want to mirror the production
configuration), but on production and shared development machines we need to
coordinate the blocks.  Here is a first cut, expressed in terms of PORT_BASE:

    9000 main production cluster
    9100 secondary production cluster
    9200 customer evaluation cluster
    9300 Claire dev cluster
    9400 Chip dev cluster
    9500 external support cluster (e.g., Intel dev host)
