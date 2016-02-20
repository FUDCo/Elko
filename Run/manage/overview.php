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

<h1>Server Cluster Overview</h1>

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

import_request_variables("p", "req_");
if (!isset($req_op)) {
    $req_op = "start";
}

$badKey = true;

if (!isset($req_masterKey)) {
    if (isset($_COOKIE["masterKey"])) {
        $req_masterKey = $_COOKIE["masterKey"];
    } else {
        $req_masterKey = "";
    }
}

if (isset($req_masterKey)) {
    $masterDir = $runPathBase . "/overview";
    $keyFile = $masterDir . "/master.key";
    if (file_exists($keyFile)) {
        $testKey = trim(file_get_contents($keyFile));
        if ($testKey !== $req_masterKey) {
            echo "<p>Invalid key";
        } else {
            $badKey = false;
        }
    } else {
        echo "<p>No master key configured";
    }
}
if ($badKey) {
    ?>
    <form enctype="multipart/form-data" action="overview.php" method="POST">
        <p>Master key:
        <input type="text" name="masterKey" />
        <input type="submit" value="Use" />
    </form>
    <?php
} else {
    setcookie("masterKey", $req_masterKey, time() + 60*60*24*365, "/");
}

if (!$badKey) {
    echo "<p>Clusters under management:";
    echo "<table border=1>";
    echo "<tr><th>Cluster ID</th><th>Web mgmt?</th><th>Run setting</th><th>Port base</th></tr>";
    $runEntries = scandir($runPathBase);
    foreach ($runEntries as $entry) {
        if (strlen($entry) > 4 && substr($entry, 0, 4) == "run.") {
            echo "<tr>";
            $clusterName = substr($entry, 4);
            echo "<td>$clusterName</td>";
            $runDir = $runPathBase . "/" . $entry;
            $ctlDir = $runDir . "/control";
            $haveCtlDir = file_exists($ctlDir);
            echo "<td align='center'>" . runFlag($haveCtlDir, true) . "</td>";
            if ($haveCtlDir) {
                $runSetting = file_exists($ctlDir . "/clusterRunOn");
            } else {
                $runSetting = $unset;
            }
            echo "<td align='center'>" . runFlag($runSetting, true) . "</td>";
            if ($haveCtlDir) {
                $portBase = "????";
            } else {
                $portBase = "---";
            }
            if (file_exists($runDir . "/odb/server-broker.json")) {
                $configJSON =
                    file_get_contents($runDir . "/odb/server-broker.json");
                $config = json_decode($configJSON);
                if (isset($config->portbase)) {
                    $portBase = $config->portbase;
                } else {
                    $portBase = 9000;
                }
            }
            echo "<td align='right'>$portBase</td>";
            echo "</tr>";
        }
    }
    echo "</table>";
}
?>

</div>
</div>
</body>
</html>
