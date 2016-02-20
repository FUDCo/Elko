<?php
@include_once("manage_environ.php");
?>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
   "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
  <title>Server Configuration Customizer - Elko Server Framework</title>
  <meta http-equiv="content-type" content="text/html; charset=UTF-8">
  <link rel="stylesheet" type="text/css" href="elko.css" >
</head>

<body>
<div id="doc">

<div>
  <div id="logo"><a href="/"><img src="elkologosmall.png" alt="small Elko logo" border="0"></a></div>
  <div id="pagetitle"><h1>Elko Server Framework</h1></div>
</div>

<div id="main">

<h1>Server Configurizer</h1>

<?php

function ds($indent = null) {
    if ($indent != null) {
        echo "<div style='margin-left:" . $indent . "em'>";
    } else {
        echo "<div>";
    }
}
function e($text) {
    echo $text;
}
function de() {
    echo("</div>");
}


function expandValue($val) {
    $type = gettype($val);
    if ($type == "boolean") {
        e($val ? "true" : "false");
    } else if ($type == "integer" || $type == "double") {
        e($val);
    } else if ($type == "string") {
        e('"'. $val . '"');
    } else if ($type == "object") {
        e("{"); expandJSON($val); e("}");
    } else if ($type == "array") {
        e("[");
        foreach ($val as $arrVal) {
            ds(2); expandValue($arrVal); e(","); de();
        }
        e("]");
    } else {
        e("unknown type: $type");
    }
}

function expandJSON($obj) {
    foreach ($obj as $propName => $propVal) {
        ds(2); e("$propName: "); expandValue($propVal); e(","); de();
    }
}

function option($label, $val) {
    echo "<option";
    if ($label == $val) {
        echo " selected";
    }
    echo ">$label</option>";
}

function portBase($config) {
    if (isset($config->portbase)) {
        return $config->portbase;
    } else {
        return 9000;
    }
}

function portBlock($config) {
    if (isset($config->portblock)) {
        return $config->portblock;
    } else {
        switch ($config->component) {
            case "context":    return 0;
            case "broker":     return 10;
            case "workshop":   return 20;
            case "gatekeeper": return 30;
            case "presence":   return 40;
            case "repository": return 50;
            case "director":   return 60;
            default:           return 0;
        }
    }
}

function updatePortConfig($config, $num, $proto, $access) {
    $listener = null;
    $changed = false;
    foreach ($config->listeners as $idx => $candidate) {
        if ($candidate->port == $num) {
            $listenerIdx = $idx;
            $listener = $candidate;
            break;
        }
    }
    if ($listener != null) {
        if (!boolParam("port_$num")) {
            unset($config->listeners[$idx]);
            $changed = true;
        } else if (is_array($access)) {
            $access = selectParam("access_$num", $access);
            if ($access != null && $listener->auth->mode != $access) {
                $listener->auth->mode = $access;
                $changed = true;
            }
        }
    } else {
        if (boolParam("port_$num")) {
            if (is_array($access)) {
                $access = selectParam("access_$num", $access);
            }
            if ($access != null) {
                $newPort = new stdClass();
                $newPort->host = '${HOST}';
                $newPort->bind = '${BIND}';
                $newPort->port = $num;
                $newPort->protocol = $proto;
                $newPort->auth = new stdClass();
                $newPort->auth->mode = $access;
                $config->listeners[] = $newPort;
                $changed = true;
            }
        }
    }
    if ($changed) {
        $newArray = array();
        foreach ($config->listeners as $elem) {
            $newArray[] = $elem;
        }
        $config->listeners = $newArray;
    }
    return $changed;
}

function portConfig($config, $num, $proto, $access) {
    $listener = null;
    foreach ($config->listeners as $candidate) {
        if ($candidate->port == $num) {
            $listener = $candidate;
            break;
        }
    }
    echo "<tr><td align='center'><input type='checkbox' name='port_$num' value='true'";
    if ($listener != null) {
        echo "checked ";
    }
    echo "/></td>";
    echo "<td align='center' style='padding-left:1em'>$proto</td>";
    echo "<td style='padding-left:1em'>";
    if (is_string($access)) {
        echo $access;
    } else {
        echo "<select name='access_$num'>";
        if ($listener != null) {
            $selected = $listener->auth->mode;
        } else {
            $selected = "";
        }
        foreach ($access as $mode) {
            option($mode, $selected);
        }
        echo "</select>";
    }
    echo "</td>";
    echo "<td style='padding-left:1em'>" . (portBase($config) + portBlock($config) + $num) . "</td></tr>";
}

function boolParam($name) {
    return isset($_REQUEST[$name]) && $_REQUEST[$name] == 'true';
}

function selectParam($name, $choices) {
    if (isset($_REQUEST[$name])) {
        $pick = $_REQUEST[$name];
        if (in_array($pick, $choices)) {
            return $pick;
        }
    }
    return null;
}

function canonicalizeConfig($config) {
    if (!isset($config->debugTimeouts)) {
        if (isset($config->entrytimeout) && $config->entrytimeout > 60) {
            $config->debugTimeouts = true;
        } else if (isset($config->reservationexpire) && $config->reservationexpire > 60) {
            $config->debugTimeouts = true;
        } else if (isset($config->httptimeout) && $config->httptimeout > 60) {
            $config->debugTimeouts = true;
        } else if (isset($config->httpselectwait) && $config->httpselectwait < 60) {
            $config->debugTimeouts = true;
        } else {
            $config->debugTimeouts = false;
        }
    }

    if (!isset($config->debuggable)) {
        $config->debuggable = false;
    }
    if (!isset($config->waitfordebugger)) {
        $config->waitfordebugger = false;
    }
}

function updateConfig($config) {
    $changed = false;
    foreach ($config->trace->levels as $tr => $level) {
        $newLevel =
            selectParam("trace_$tr",
                        array("ERROR", "WARNING", "WORLD", "USAGE", "EVENT",
                              "DEBUG", "VERBOSE"));
        if ($newLevel != null && $newLevel != $level) {
            $config->trace->levels->$tr = $newLevel;
            $changed = true;
        }
    }

    if (boolParam("msgdiagnostics") != $config->msgdiagnostics) {
        $config->msgdiagnostics = !$config->msgdiagnostics;
        $changed = true;
    }
    if (boolParam("debugtimeouts") != $config->debugTimeouts) {
        $config->debugTimeouts = !$config->debugTimeouts;
        $changed = true;
    }
    if (boolParam("debuggable") != $config->debuggable) {
        $config->debuggable = !$config->debuggable;
        $changed = true;
    }
    if (boolParam("waitfordebugger") != $config->waitfordebugger) {
        $config->waitfordebugger = !$config->waitfordebugger;
        $changed = true;
    }

    if ($config->component == "context") {
        $changed |= updatePortConfig($config, 0, "tcp",  "reservation");
        $changed |= updatePortConfig($config, 1, "http", "reservation");
        $changed |= updatePortConfig($config, 2, "rtcp", "reservation");
        $changed |= updatePortConfig($config, 3, "tcp",  "open");
        $changed |= updatePortConfig($config, 4, "http", "open");
        $changed |= updatePortConfig($config, 5, "rtcp", "open");
        $changed |= updatePortConfig($config, 6, "tcp", array("open", "admin"));
        $changed |= updatePortConfig($config, 8, "zmq", array("open", "admin"));
    } else if ($config->component == "director") {
        $changed |= updatePortConfig($config, 0, "tcp",  "user");
        $changed |= updatePortConfig($config, 1, "http", "user");
        $changed |= updatePortConfig($config, 2, "rtcp", "user");
        $changed |= updatePortConfig($config, 3, "tcp",  "open");
        $changed |= updatePortConfig($config, 4, "http", "open");
        $changed |= updatePortConfig($config, 5, "rtcp", "open");
    }
    return $changed;
}

import_request_variables("gp", "req_");
if (!isset($req_op)) {
    $req_op = "get";
}

require_once("validatecluster.php");

if (!isset($req_server) && isset($_COOKIE["server"])) {
    $req_server = $_COOKIE["server"];
}
if (isset($req_server)) {
    setcookie("server", $req_server, time() + 60*60*24*365, "/");
} else {
    $badCluster = true;
}

if (!$badCluster) {
    echo "<form enctype='multipart/form-data' action='configurize.php' method='POST'>";

    $configFile = file_get_contents($runDir . "/odb/server-$req_server.json");
    $config = json_decode($configFile);
    canonicalizeConfig($config);
    $changed = false;
    if ($req_op == "update") {
        $changed = updateConfig($config);
    }

    echo "<h2>Cluster: <i>$req_clusterID</i><br>";
    echo "Server: <i>" . $config->name . "</i></h2>";

    $portBase = portBase($config);
    $portBlock = portBlock($config);

    ds(); e("<b>Server type:</b> " . $config->component); de();
    ds(); e("<b>Cluster port base:</b> " . $portBase); de();
    ds(); e("<b>Server port block offset:</b> " . $portBlock); de();
    ds(); e("<p><b>Trace levels:</b>");
    ds(2);
    echo "<table>";
    foreach ($config->trace->levels as $tr => $level) {
        echo "<tr><td><tt>$tr</tt></td><td><select name='trace_$tr'>";
        option("ERROR", $level);
        option("WARNING", $level);
        option("WORLD", $level);
        option("USAGE", $level);
        option("EVENT", $level);
        option("DEBUG", $level);
        option("VERBOSE", $level);
        echo "</select></td></tr>";
    }
    echo "</table>";
    de();
    ds();
    echo "<b>Message diagnostics:</b> <input type='checkbox' name='msgdiagnostics' value='true'";
    if ($config->msgdiagnostics) {
        echo "checked ";
    }
    echo "/>";
    de();

    if ($config->debugTimeouts) {
        $config->entrytimeout = 300;
        $config->reservationexpire = 300;
        $config->httptimeout = 180;
        $config->httpselectwait = 30;
    } else {
        $config->entrytimeout = 30;
        $config->reservationexpire = 30;
        $config->httptimeout = 15;
        $config->httpselectwait = 300;
    }
    echo "<b>Debug:</b>";
    ds(2);
    echo "<b>Use debug timeouts:</b> <input type='checkbox' name='debugtimeouts' value='true'";
    if ($config->debugTimeouts) {
        echo "checked ";
    }
    echo "/>";
    de();
    ds(2);
    echo "<b>Enable debugger access:</b> <input type='checkbox' name='debuggable' value='true'";
    if ($config->debuggable) {
        echo "checked ";
    }
    echo "/> (port " . (portBase($config) + portBlock($config) + 9) . ")";
    de();
    ds(2);
    echo "<b>Suspend at startup:</b> <input type='checkbox' name='waitfordebugger' value='true'";
    if ($config->waitfordebugger) {
        echo "checked ";
    }
    if (!$config->debuggable) {
        echo "disabled ";
    }
    echo "/>";
    de();

    if ($config->component == "context" || $config->component == "director") {
        echo "<b>Ports:</b>"; ds(2);
        echo "<table>";
        echo "<tr><th>Enable</th><th style='padding-left:1em'>Protocol</th><th style='padding-left:1em'>Access</th><th style='padding-left:1em'>Port number</th></tr>";
        if ($config->component == "context") {
            portConfig($config, 0, "tcp",  "reservation");
            portConfig($config, 1, "http", "reservation");
            portConfig($config, 2, "rtcp", "reservation");
            portConfig($config, 3, "tcp",  "open");
            portConfig($config, 4, "http", "open");
            portConfig($config, 5, "rtcp", "open");
            portConfig($config, 6, "tcp", array("open", "admin"));
            portConfig($config, 8, "zmq", array("open", "admin"));
        } else if ($config->component == "director") {
            portConfig($config, 0, "tcp",  "user");
            portConfig($config, 1, "http", "user");
            portConfig($config, 2, "rtcp", "user");
            portConfig($config, 3, "tcp",  "open");
            portConfig($config, 4, "http", "open");
            portConfig($config, 5, "rtcp", "open");
        }
        echo "</table>";
        de();
    }

    echo "<input type='hidden' name='op' value='update' />";
    echo "<input type='submit' value='Update' />";
    echo "</form>";
    if ($changed) {
        file_put_contents($runDir . "/odb/server-$req_server.json", json_encode($config));
    }
    //echo "<p>Configuration:\n";
    //ds(); expandJSON($config); de();
}
?>

</div>
</div>
</body>
</html>
