<?php
@include_once("manage_environ.php");
?>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
   "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
  <title>Server Cluster Manager - Elko Server Framework</title>
  <meta http-equiv="content-type" content="text/html; charset=UTF-8">
  <link rel="stylesheet" type="text/css" href="elko.css" >
  <script src="admin.js"></script>
</head>

<body>
<div id="doc">

<div>
  <div id="logo"><a href="/"><img src="elkologosmall.png" alt="small Elko logo" border="0"></a></div>
  <div id="pagetitle"><h1>Elko Server Framework</h1></div>
</div>

<div id="main">

<h1>Server Cluster Manager</h1>

<?php

function runFlag($flag, $active) {
    $result = $active ? "" : "<span style='color:gray'>";
    if (isset($flag)) {
        $result .= $flag ? "ON" : "OFF";
    } else {
        $result .= "---";
    }
    $result .= $active ? "" : "</span>";
    return $result;
}

function simpleButton($url, $op, $text, $enable = true, $newWindow = false,
                      $otherVars = null)
{
    global $req_clusterID;
    global $req_clusterKey;

    if ($enable) {
        $disable = "";
    } else {
        $disable = " disabled";
    }
    echo "<input type='button' value='$text' onclick='postToURL(\"$url\", { op:\"$op\", clusterID:\"$req_clusterID\", clusterKey:\"$req_clusterKey\"";
    if ($otherVars != null) {
        foreach ($otherVars as $key => $val) {
            echo ", $key: \"$val\"";
        }
    }
    echo "}, " . ($newWindow ? "true" : "false") . ")'$disable />";
}

function runButton($comp, $isRunning, $active) {
    if ($comp == "broker") {
        $otherVars = null;
        if ($isRunning) {
            $verb = "stopall";
            $text = "Stop Cluster";
        } else {
            $verb = "startall";
            $text = "Start Cluster";
        }
    } else {
        $otherVars = array("server" => $comp);
        if ($isRunning) {
            $verb = "stopserver";
            $text = "Stop";
        } else {
            $verb = "startserver";
            $text = "Start";
        }
    }
    if ($active || $comp == "broker") {
        $enable = true;
        $disable = "";
    } else {
        $enable = false;
        $disable = " disabled";
    }
    simpleButton("manage.php", $verb, $text, $enable, false, $otherVars);
}

function runSetting($entry) {
    if (isset($entry->on)) {
        return $entry->on;
    } else if (isset($entry->initial)) {
        return $entry->initial;
    } else {
        return false;
    }
}

function component($status, $compName) {
    if (isset($status->components[$compName])) {
        return $status->components[$compName];
    } else {
        $comp = new stdClass();
        $comp->isBroker = false;
        $status->components[$compName] = $comp;
        return $comp;
    }
}

function lookupServerStatus($queryBroker) {
    global $ctlDir;
    global $runDir;
    global $runParams;
    
    $result = new stdClass();
    $result->clusterRun = file_exists($ctlDir . "/clusterRunOn");
    $result->components = array();
    $brokerComp = component($result, 'broker');
    $brokerComp->runSetting = $result->clusterRun;
    $brokerComp->isBroker = true;
    
    if (file_exists($runDir . "/odb/launchertable.json")) {
        $launcherTableJSON =
            file_get_contents($runDir . "/odb/launchertable.json");
        $launcherTable = json_decode($launcherTableJSON);
        foreach ($launcherTable->launchers as $entry) {
            $comp = component($result, $entry->name);
            $comp->runSetting = runSetting($entry);
        }
    }
    
    $odbEntries = scandir($runDir . "/odb");
    foreach ($odbEntries as $entry) {
        if (strlen($entry) > 12 && substr($entry, 0, 7) == "server-" &&
                substr($entry, -5) == ".json") {
            $compName = substr($entry, 7, -5);
            $comp = component($result, $compName);
            $comp->haveConfig = true;
        }
    }
    
    if ($queryBroker) {
        $queryResultRaw = trim(`../common/brokercmd load $runParams`);
        $queryResult = json_decode($queryResultRaw);
        if (isset($queryResult->desc)) {
            foreach ($queryResult->desc as $desc) {
                $comp = component($result, $desc->label);
                $comp->load = $desc->load;
                $comp->provider = $desc->provider;
                $comp->isRunning = true;
            }
        }
        $result->emptyQueryResult = $queryResultRaw == "";
        $brokerComp->isRunning = !$result->emptyQueryResult;
    } else {
        $result->emptyQueryResult = false;
    }
    //error_log("lookupServerStatus returns " . print_r($result, true));
    return $result;
}

import_request_variables("p", "req_");
if (!isset($req_op)) {
    $req_op = "start";
}

$baseClassDescs = "classes-app,classes-bank";
$baseStatics = "";

require_once("validatecluster.php");

if (!$badCluster) {
    $runParams = "";
    if (file_exists($runDir . "/broker.runparams")) {
        $runParams = trim(file_get_contents($runDir . "/broker.runparams"));
    }

    $possibleMods = scandir($runDir . "/localmodules");
    $mods = array();
    foreach ($possibleMods as $maybeMod) {
        if (strlen($maybeMod) > 4 && substr($maybeMod, -4) == ".jar") {
            $mods[] = substr($maybeMod, 0, -4);
        }
    }
    
    $doStatusQuery = false;
    if ($req_op == "statusquery") {
        $doStatusQuery = true;
    } else if ($req_op == "moduleupdate") {
        //echo "<p> POST: "; print_r($_POST);
        //echo "<p> FILES: "; print_r($_FILES);
        $installModule = false;
        $uploadModule = false;
        $removeModule = false;
        $errStr = null;
        $moduleName = "<none>";
        if (isset($req_addmod)) {
            if ($req_newmodule) {
                $installModule = true;
                $uploadModule = true;
                $moduleName = $req_newmodule;
            } else {
                $errStr = "Requested to add module but no module name given";
            }
        } else {
            $gotit = false;
            foreach ($mods as $mod) {
                if (isset($_POST["remove_$mod"])) {
                    $removeModule = true;
                    $moduleName = $mod;
                    break;
                } if (isset($_POST["upload_$mod"])) {
                    $uploadModule = true;
                    $moduleName = $mod;
                    break;
                }
            }
            if (!$uploadModule && !$removeModule) {
                $errStr = "Don't know what you want me to do here";
            }
        }
        $deployPath = $runDir . "/localmodules/" . $moduleName . ".jar";
        if ($uploadModule) {
            $nameAsUploaded = $_FILES['uploadedfile']['name'];
            $tmpName = $_FILES['uploadedfile']['tmp_name'];
            $targetPath = $runDir . "/uploads/" . $moduleName . ".jar"; 
            $err = $_FILES['uploadedfile']['error'];
            
            if ($err === UPLOAD_ERR_INI_SIZE || $err == UPLOAD_ERR_FORM_SIZE) {
                $errStr = "File too big.";
            } else if ($err == UPLOAD_ERR_PARTIAL) {
                $errStr = "Upload failed to send the entire file.";
            } else if ($err == UPLOAD_ERR_NO_FILE) {
                $errStr = "Upload didn''t actually send a file.";
            } else if ($err != UPLOAD_ERR_OK) {
                $errStr = "Internal problem ($err).";
            }
            
            if ($err != UPLOAD_ERR_OK) {
                $errStr = "Upload failure: " . $errStr;
            } else if (move_uploaded_file($tmpName, $targetPath)) {
                echo "<p>The file " .  basename($nameAsUploaded) . " has been uploaded.";
            } else {
                $err = UPLOAD_ERR_NO_FILE;
                if ($errStr == null) {
                    $errStr = "Unable to process uploaded file.";
                }
            }
            
            if ($err == UPLOAD_ERR_OK) {
                $validationResult = trim(`../common/validateModule $targetPath`);
                $validationWords = explode(" ", $validationResult);
                if ($validationWords[0] != "ok") {
                    @unlink($targetPath);
                    $errStr = "Validation failed, new module installation aborted.";
                }
            }
        }
        if ($errStr == null) {
            echo "<p>Shutting down context server...";
            $shutdownResult = `../common/brokercmd stopcontext $runParams`;
            echo "<br>Shutdown result: $shutdownResult";
            $rewriteConfig = false;
            if ($removeModule) {
                foreach ($mods as $idx => $mod) {
                    if ($mod == $moduleName) {
                        unset($mods[$idx]);
                        $rewriteConfig = true;
                        break;
                    }
                }
                if ($rewriteConfig) {
                    unlink($deployPath);
                }
            } else if ($installModule) {
                $rewriteConfig = true;
                foreach ($mods as $idx => $mod) {
                    if ($mod == $moduleName) {
                        $rewriteConfig = false;
                        break;
                    }
                }
                if ($rewriteConfig) {
                    $mods[] = $moduleName;
                }
            }
            if ($rewriteConfig) {
                $classDesc = "conf.context.classdesc=$baseClassDescs";
                if ($baseClassDescs == "") {
                    $appendingClasses = false;
                } else {
                    $appendingClasses = true;
                }
                $statics = "conf.context.statics=$baseStatics";
                if ($baseStatics == "") {
                    $appendingStatics = false;
                } else {
                    $appendingStatics = true;
                }
                foreach ($mods as $mod) {
                    if ($appendingClasses) {
                        $classDesc .= ",";
                    }
                    $classDesc .= "classes-$mod";
                    $appendingClasses = true;
                    if ($appendingStatics) {
                        $statics .= ",";
                    }
                    $statics .= "statics-$mod";
                    $appendingStatics = true;
                }
                $fyle = fopen($runDir . "/modules.properties", "w");
                if (!$fyle) {
                    $errStr = "Unable to open module properties file for write";
                } else {
                    fwrite($fyle, "$classDesc\n$statics\n");
                    fclose($fyle);
                }
            }
            if ($errStr == null && $uploadModule) {
                echo "<p>Deploying module...";
                unlink($deployPath);
                copy($targetPath, $deployPath);
            }
        }
        if ($errStr == null) {
            echo "<p>Restarting context server...";
            $startResult = `../common/brokercmd startcontext $runParams`;
            echo "<br>Start result: $startResult";
            $doStatusQuery = true;
        } else {
            echo "<p>Error: $errStr\n";
        }
    } else if ($req_op == "startall") {
        echo "<br>Starting servers...";
        touch("$ctlDir/clusterRunOn");
        sleep(2);
        $doStatusQuery = true;
    } else if ($req_op == "stopall") {
        echo "<p>Shutting down servers...";
        $shutdownResult = `../common/clusterStop $req_clusterID`;
        echo "<br>Shutdown result: $shutdownResult";
        $doStatusQuery = true;
    } else if ($req_op == "startserver") {
        $status = lookupServerStatus(true);
        if (isset($status->components[$req_server])) {
            echo "<p>Starting $req_server...";
            $startResult = `../common/brokercmd startcomponent $runParams $req_server`;
            echo "<br>Start result: $startResult";
        } else {
            echo "<p>Error: unknown component '$req_server'";
        }
        $doStatusQuery = true;
    } else if ($req_op == "stopserver") {
        $status = lookupServerStatus(true);
        if (isset($status->components[$req_server])) {
            echo "<p>Stopping $req_server...";
            $stopResult = `../common/brokercmd stopcomponent $runParams $req_server`;
            echo "<br>Stop result: $stopResult";
        } else {
            echo "<p>Error: unknown component '$req_server'";
        }
        $doStatusQuery = true;
    } else if ($req_op == "logflush") {
        $logs = scandir($runDir . "/logs");
        foreach ($logs as $log) {
            unlink($runDir . "/logs/" . $log);
        }
    }
    echo "<h2>Cluster: <i>$req_clusterID</i></h2>";

    $status = lookupServerStatus($doStatusQuery);

    echo "<p>Cluster run setting: " . runFlag($status->clusterRun, true);
    ?>
    
    <table border=1>
    <tr><th>Server</th><th>Run Setting</th>
    <?php
    if ($doStatusQuery) {
        echo "<th>Load</th><th>Provider #</th>";
    }
    echo "<th align='center'>Actions</th>";
    echo "</tr>\n";
    foreach ($status->components as $compName => $comp) {
        echo "<tr><td>$compName</td>";
        echo "<td align='center'>" . runFlag($comp->runSetting, $status->clusterRun) . "</td>";
        $isRunning = false;
        if ($doStatusQuery) {
            if ($comp->isBroker) {
                if ($status->emptyQueryResult) {
                    echo "<td align='right' style='color:red;'>not running</span></td><td align='right'>---</td>";
                } else {
                    echo "<td align='right'>---</td><td align='right'>0</td>";
                    $isRunning = true;
                }
            } else if (isset($comp->isRunning)) {
                echo "<td align='right'>$comp->load</td><td align='right'>$comp->provider</td>";
                $isRunning = true;
            } else {
                echo "<td align='right'>not running</td><td align='right'>---</td>";
            }
        } else {
            $isRunning = $comp->runSetting && $status->clusterRun;
        }
        echo "<td align='right'>";
        runButton($compName, $isRunning, $status->clusterRun);
        if (isset($comp->haveConfig)) {
            simpleButton("configurize.php", "get", "Configure", true, true,
                         array("server" => $compName));
        }
        echo "</td></tr>\n";
    }
    echo "</table>\n";

    echo "Query running server status:";
    simpleButton("manage.php", "statusquery", "Query");
    ?>

    <form enctype="multipart/form-data" action="manage.php" method="POST">
        <p>Change cluster ID:
        <input type="text" name="clusterID" />
        Cluster key:
        <input type="text" name="clusterKey" />
        <input type="submit" value="Change" />
    </form>
    
    <hr>
    
    <h2>Modules</h2>
    
    <h3>Installed Modules</h3>
    
    <form enctype="multipart/form-data" action="manage.php" method="POST">
      <input type="hidden" name="MAX_FILE_SIZE" value="10000000" />
      <input type="hidden" name="op" value="moduleupdate" />
      <input type="hidden" name="clusterID" value="<?php echo $req_clusterID;?>" />
      <table border=1>
      <tr><th>Module</th><th align="center">Actions</th><tr>
    <?php
    foreach ($mods as $mod) {
        echo "<tr><td>$mod</td><td><input name='upload_$mod' type='submit' value='Update' /><input name='remove_$mod' type='submit' value='Remove' /></td></tr>";
    }
    echo "<tr><td><input type='text' name='newmodule' /></td><td><input name='addmod' type='submit' value='Add' /></td></tr>";
    echo "</table>";
    ?>
    <?php if ($status->clusterRun && isset($status->components['context']) && $status->components['context']->runSetting) { ?>
        <p><i>NOTE: module updates will include a Context Server restart, so
        don&rsquo;t change the module configuration unless your application can
        tolerate a server restart right now.</i></p>
    <?php } ?>

    For additions or updates, you must also choose a .jar file to upload: <input name="uploadedfile" type="file" />
    </form>

    <hr>
    <h2>Run Logs</h2>
    <?php
    $possibleLogs = scandir($runDir . "/logs");
    $prevCategory = null;
    echo "<table>";
    foreach ($possibleLogs as $log) {
        $prefix = substr($log, 0, 5);
        $category = $prefix;
        switch ($prefix) {
            case "brok.": $label = "Broker";   break;
            case "dire.": $label = "Director"; break;
            case "cont.": $label = "Context";  break;
            case "pres.": $label = "Presence"; break;
            case "work.": $label = "Workshop"; break;
            default: $category = null; break;
        }
        if ($category != null) {
            if ($category != $prevCategory) {
                $prevCategory = $category;
                echo "<tr><td colspan=2 align='left'><h4>$label</h4></td></tr>";
            }
            $ts = substr($log, 5);
            $year = substr($ts, 0, 4);
            $month = substr($ts, 4, 2);
            if ($month[0] == '0') {
                $month = "&nbsp;" . $month[1];
            }
            $day = substr($ts, 6, 2);
            $hour = substr($ts, 8, 2);
            $minute = substr($ts, 10, 2);
            $second = substr($ts, 12, 2);
            echo "<tr><td width=20></td>";
            echo "<td><a href='logs/$req_clusterID/$log' target='_blank'>$year $month/$day $hour:$minute:$second</a></td></tr>";
        }
    }
    echo "</table>";
    if ($prevCategory == null) {
        echo "<h4>No logs</h4>";
    } else if (!$status->clusterRun) {
        simpleButton("manage.php", "logflush", "Clear logs");
    }
}
?>

</div>
</div>
</body>
</html>
