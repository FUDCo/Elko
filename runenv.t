Run/ -- root of the runtime environment
  common/ -- files (typically shell scripts) shared in common by all runtimes 
    setenvvars -- set universal shell and environment variables
    setlocvars -- set shell and environment variables specific to this host
    <<setlocvars.${USER}>> -- set shell & env vars specific to user $USER
  manage/ -- scripts and PHP web pages for managing server clusters
    clusterQuery.sh -- send loaddesc & servicedesc broker requests, print reply
    clusterStartAll.sh -- command a cluster to start all its servers *BAD*
    clusterStartOne.sh -- command a cluster to start one server *BAD*
    clusterStartRest.sh -- command a cluster to start all servers but one *BAD*
    contextShutdown.sh -- send shutdown context server broker request *WRONG*
    debugStart.sh -- command a cluster to start all servers with one in jdb
    fullShutdown.sh -- send shutdown all broker request
    loadQuery.sh -- send loaddesc broker request, print reply
    manage.php -- control console web app *WORK IN PROGRESS*
    manage_environ.php -- environment-specific var settings for manage.php
    restart.sh -- script run by cron job to restart dead servers *WRONG*
    restarter.cron -- crontab to run restart.sh *PROBLEMATIC*
    serviceQuery.sh -- send servicedesc broker request, print reply
    startAll.sh -- command current cluster to start all servers *BAD*
    startOne.sh -- command current cluster to start one server
    startRest.sh -- command current cluster to start all but one server *BAD*
    <elko.css> -- CSS file used by manage.php
    <elkologosmall.png> -- graphic file used by manage.php
    <title_bg.png> -- graphic file used by manage.php
    validateModule.sh -- validate an uploaded module JAR file *PLACEHOLDER*
  mongosupport/ -- support files for MongoDB
    mongohelper.js -- useful functions for Mongo shell
  <notes>/ -- various notes
    <<notes>> -- various notes
  run.${CLUSTER}/ -- runtime environment for cluster $CLUSTER
    broker.runparams -- host and port number of most recently launched broker
    cluster-components -- list of servers in cluster, one per line
    control/ -- directory of semaphore files for server control *WRONG*
       ${COMPONENT}ShouldBeRunning -- flag that $COMPONENT should be running
       serverShouldBeRunning -- flag that cluster should be running
       ${COMPONENT}IsRunning -- flag that $COMPONENT was started *PROBLEMATIC*
    launch.${COMPONENT} -- startup script for $COMPONENT
    localmodules/ -- directory full of JAR files for installed uploaded modules
      ${MODULE}.jar -- JAR file for $MODULE
    logs/ -- directory where log files go
      <<logfiles>> -- various logfiles
    modules.properties -- propes file for context server's installed modules
    odb/ -- flat-file ODB to hold broker configuration
      classes.json -- necessary classes file for a well-formed ODB
      launchertable.json -- serialized table of broker's configured launchers
    <<setlocvars>> -- set shell and environment vars specific to this cluster
    <<setlocvars.${USER}>> -- set shell & env vars for this cluster for $USER
    uploads/ -- directory for uploaded JAR files prior to deployment
      ${MODULE}.jar -- uploaded JAR file for $MODULE

where:
  <file>  file is incidental
  <<file>> file is optional
  USER = userid of user running the server, e.g., chip, elko, root, etc.
  CLUSTER = cluster ID (which bundle of servers this is, e.g., prod, chipdev,
    eval, etc.)
  COMPONENT = server suite component, e.g., context, broker, director, etc.
  MODULE = application or game module , e.g., Dice, BadPets, Moab, etc.
