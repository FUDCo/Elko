<?php

function boolval($val) {
    if (isset($val)) {
        return $val ? "true" : "false";
    } else {
        return $val;
    }
}

class LaunchGen {
    private $out;
    private $portBase;
    private $didSep;
    
    private function pstr($str) {
        fwrite($this->out, "    $str \\\n");
        $this->didSep = false;
    }

    private function psep() {
        if (!$this->didSep) {
            fwrite($this->out, "    \\\n");
            $this->didSep = true;
        }
    }

    private function pprop($name, $val, $defval = null) {
        if (isset($val)) {
            $this->pstr("$name=$val");
        } else if ($defval != null) {
            $this->pstr("$name=$defval");
        }
    }

    private function pauth($base, $auth) {
        $this->pprop("$base.auth.mode", $auth->mode);
        if ($auth->mode == "password") {
            $this->pprop("$base.auth.code", $auth->code);
        }
    }

    private function phost($base, $desc, $defHost, $defPort) {
        if (isset($desc->host)) {
            $host = $desc->host;
        } else {
            $host = $defHost;
        }
        if (isset($desc->port)) {
            $port = $this->portBase + $desc->portOffset + $desc->port;
        } else {
            $port = $defPort;
        }
        $this->pprop("$base.host", "$host:$port");
        if (isset($desc->auth)) {
            $this->pauth("$base", $desc->auth);
        }
    }

    public function generateLaunchScript($server, $componentTag) {
        $configJSON = file_get_contents("odb/server-$server.json");
        $config = json_decode($configJSON);
        
        $component = $config->component;
        
        if (!isset($componentTag)) {
            if (isset($config->tag)) {
                $componentTag = $config->tag;
            } else {
                $componentTag = substr($component, 0, 4);
            }
        }
        
        $this->portBase = isset($config->portbase) ? $config->portbase : 9000;
        $contextPortOffset = isset($config->contextportoffset) ?
            $config->contextportoffset : 0;
        $brokerPortOffset = isset($config->brokerportoffset) ?
            $config->brokerportoffset : 10;
        $workshopPortOffset = isset($config->workshopportoffset) ?
            $config->workshopportoffset : 20;
        $gatekeeperPortOffset = isset($config->gatekeeperportoffset) ?
            $config->gatekeeperportoffset : 30;
        $presencePortOffset = isset($config->presenceportoffset) ?
            $config->presenceportoffset : 40;
        $repositoryPortOffset = isset($config->repositoryportoffset) ?
            $config->repositoryportoffset : 50;
        $directorPortOffset = isset($config->directorportoffset) ?
            $config->directorportoffset : 60;
        if (isset($config->portoffset)) {
            $portOffset = $config->portoffset;
        } else if ($component == "context") {
            $portOffset = $contextPortOffset;
        } else if ($component == "broker") {
            $portOffset = $brokerPortOffset;
        } else if ($component == "director") {
            $portOffset = $directorPortOffset;
        } else if ($component == "workshop") {
            $portOffset = $workshopPortOffset;
        } else if ($component == "gatekeeper") {
            $portOffset = $gatekeeperPortOffset;
        } else if ($component == "presence") {
            $portOffset = $presencePortOffset;
        } else if ($component == "repository") {
            $portOffset = $repositoryPortOffset;
        } else {
            $portOfset = 0;
        }

        if (isset($config->debuggable) && $config->debuggable) {
            $debugPort = $this->portBase + $portOffset + 9;
        } else {
            $debugPort = -1;
        }
        
        $nl = " \\\n";
        
        fwrite($this->out, '${JAVA_RUN} ');
        if ($debugPort > 0) {
            if (isset($config->waitfordebugger) && $config->waitfordebugger) {
                $suspendFlag = "y";
            } else {
                $suspendFlag = "n";
            }
            fwrite($this->out, "-Xdebug -Xrunjdwp:transport=dt_socket,address=$debugPort,server=y,suspend=$suspendFlag ");
        }
        fwrite($this->out, 'org.elkoserver.foundation.boot.Boot'.$nl);
        
        $trace = $config->trace;
        foreach ($trace->levels as $level => $setting) {
            $this->pprop("trace_$level", $setting);
        }
        $this->pprop("tracelog_tag", $trace->tag, $componentTag);
        $this->pprop("tracelog_dir", $trace->dir, "./log");
        $this->pprop("tracelog_rollover", $trace->rollover);
        $this->psep();
        
        $this->pprop("conf.$component.name", $config->name, $server);
        $this->pprop("conf.$component.shutdownpassword",
                     $config->shutdownpassword);
        $this->pprop("conf.comm.jsonstrictness",
                     boolval($config->jsonstrictness));
        $this->pprop("conf.msgdiagnostics", boolval($config->msgdiagnostics));
        $this->pprop("conf.load.time", $config->loadtime);
        $this->psep();
        
        $usingHTTP = false;
        $usingRTCP = false;
        $usingDirector = false;
        
        for ($i = 0; $i < count($config->listeners); ++$i) {
            $listener = $config->listeners[$i];
            $pname = "conf.listen";
            if ($i != 0) {
                $pname = $pname . $i;
            }
            $port = $this->portBase + $portOffset + $listener->port;
            $this->pprop("$pname.host", "$listener->host:$port", '${HOST}');
            $this->pprop("$pname.bind", "$listener->bind:$port", '${BIND}');
            $this->pprop("$pname.protocol", $listener->protocol);
            $this->pprop("$pname.allow", $listener->allow);
            $this->pprop("$pname.secure", boolval($listener->secure));
            if ($listener->protocol == "http") {
                $this->pprop("$pname.root", $listener->root);
                $this->pprop("$pname.domain", $listener->domain);
                $usingHTTP = true;
            } else if ($listener->protocol == "rtcp") {
                $usingRTCP = true;
            } else if ($listener->protocol == "ws") {
                $this->pprop("$pname.sock", $listener->sock);
            } else if ($listener->protocol == "zmq") {
                $this->pprop("$pname.class", $listener->class,
                    "org.elkoserver.foundation.net.zmq.ZeroMQConnectionManager");
            }
            if (isset($listener->auth)) {
                $this->pauth("$pname", $listener->auth);
                if ($listener->auth->mode == "reservation") {
                    $usingDirector = true;
                }
            }
            $this->psep();
        }
        if ($usingHTTP) {
            $this->pprop("conf.comm.httptimeout", $config->httptimeout);
            $this->pprop("conf.comm.httpselectwait", $config->httpselectwait);
        }
        if ($usingRTCP) {
            $this->pprop("conf.comm.rtcptimeout", $config->rtcptimeout);
            $this->pprop("conf.comm.rtcpdisconntimeout",
                         $config->rtcpdisconntimeout);
            $this->pprop("conf.comm.rtcpbacklog", $config->rtcpbacklog);
        }
        if (isset($config->ssl)) {
            $ssl = $config->ssl;
            $this->pprop("conf.ssl.enable", "true");
            $this->pprop("conf.ssl.keyfile", $ssl->keyfile);
            $this->pprop("conf.ssl.keypassword", $ssl->keypassword);
            $this->pprop("conf.ssl.keystoretype", $ssl->keystoretype);
            $this->pprop("conf.ssl.keymanageralgorithm",
                         $ssl->keymanageralgorithm);
        }
        $this->psep();
        
        if ($component == "context") {
            $this->pprop("conf.register.auto", boolval($config->registerauto));
            $this->pprop("conf.context.entrytimeout", $config->entrytimeout);
            $this->pstr("-properties modules.properties");
            if ($usingDirector) {
                $this->pprop("conf.context.reservationexpire",
                             $config->reservationexpire);
            }
            if (isset($config->director)) {
                $director = $config->director;
                if (isset($director->auto) && $director->auto) {
                    $this->pprop("conf.register.auto",
                                 boolval($director->auto));
                } else {
                    $director->portOffset = $directorPortOffset;
                    $this->phost("conf.register", $director, '${HOST}',
                                 '${DIRECTOR_PORT}');
                }
            }
            if (isset($config->presence)) {
                $presence = $config->presence;
                if (isset($presence->auto) && $presence->auto) {
                    $this->pprop("conf.presence.auto",
                                 boolval($presence->auto));
                } else {
                    $presence->portOffset = $presencePortOffset;
                    $this->phost("conf.presence", $presence, '${HOST}',
                                 '${PRESENCE_PORT}');
                }
            }
            $this->psep();
        } else if ($component == "broker") {
            $this->pprop("conf.broker.startmode", '${START_MODE}');
            $this->psep();
        } else if ($component == "workshop") {
            $this->pprop("conf.workshop.classdesc", $config->classdescbase);
            $this->psep();
        } else if ($component == "gatekeeper") {
            $this->pprop("conf.register.auto", boolval($config->registerauto));
            $this->pprop("conf.gate.classdesc", $config->classdescbase);
            if (isset($config->director)) {
                $config->director->portOffset = $directorPortOffset;
                $this->phost("conf.register", $config->director,
                             '${HOST}', '${DIRECTOR_PORT}');
                $this->pprop("conf.gate.director.auto",
                             boolval($config->director->auto));
            }
            $this->psep();
        } else if ($component == "repository") {
            $this->pprop("conf.repository.service", $config->service);
            $this->psep();
        }
        
        if ($component != "broker" && isset($config->broker)) {
            $config->broker->portOffset = $brokerPortOffset;
            $this->phost("conf.broker", $config->broker,
                         '${HOST}', '${BROKER_PORT}');
            $this->psep();
        }
        
        if (isset($config->odb)) {
            $odb = $config->odb;
            if ($odb->kind == "mongo") {
                $this->pprop("conf.$component.odb", "mongo");
                $this->pprop("conf.$component.odb.mongo.hostport",
                             $odb->hostport, '${MONGOHOST}');
                $this->pprop("conf.$component.objstore", $odb->objstore,
                    "org.elkoserver.objdb.store.mongostore.MongoObjectStore");
            } else if ($odb->kind == "file") {
                $this->pprop("conf.$component.odb", $odb->repodir);
                $this->pprop("conf.$component.objstore", $odb->objstore,
                    "org.elkoserver.objdb.store.filestore.FileObjectStore");
            } else if ($odb->kind == "repository") {
                $repo = $config->repo;
                $repo->portOffset = $repositoryPortOffset;
                $this->phost("conf.$component.repository", $repo,
                             '${HOST}', '${REPO_PORT}');
                $this->pprop("conf.$component.repository.service",
                             $repo->service, "contextdb");
                $this->pprop("conf.$component.repository.dontlog",
                             boolval($repo->dontlog));
            }
            $this->psep();
        }
        
        if (isset($config->misc)) {
            $misc = $config->misc;
            foreach ($misc as $prop => $val) {
                $this->pprop("conf.$prop", $val);
            }
            $this->psep();
        }
        
        $this->pstr($config->bootclass);
        fwrite($this->out, '    $* </dev/null &>/dev/null &'."\n");
    }

    function __construct($outFileName) {
        if ($outFileName == "-") {
            $this->out = STDOUT;
        } else {
            $this->out = fopen($outFileName, "w");
        }
        $this->didSep = false;
    }

    static public function genLaunch($outFileName, $server, $componentTag) {
        $genner = new LaunchGen($outFileName);
        $genner->generateLaunchScript($server, $componentTag);
    }
}
?>
