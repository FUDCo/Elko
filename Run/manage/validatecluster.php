<?php

$badCluster = false;

if (!isset($req_clusterID) && isset($_COOKIE["clusterID"])) {
    $req_clusterID = $_COOKIE["clusterID"];
}
if (!isset($req_clusterKey)) {
    if (isset($_COOKIE["clusterKey"])) {
        $req_clusterKey = $_COOKIE["clusterKey"];
    } else {
        $req_clusterKey = "";
    }
}

if (isset($req_clusterID)) {
    $runDir = $runPathBase . "/run." . $req_clusterID;
    $ctlDir = $runDir . "/control";
    $keyFile = $runDir . "/cluster.key";
    if (!file_exists($runDir)) {
        echo "<p>I have no cluster named $req_clusterID";
        $badCluster = true;
    } else if (!file_exists($ctlDir)) {
        echo "<p>Cluster $req_clusterID is not configured for web management";
        $badCluster = true;
    } else if (file_exists($keyFile)) {
        $fyle = fopen($runDir . "/testw", "w");
        if (!$fyle) {
            echo "<p>Server configuration error.  Please complain.";
            error_log("file permissions wrong on run directory?");
            $badCluster = true;
        } else {
            fclose($fyle);
            $testKey = trim(file_get_contents($keyFile));
            if ($testKey !== $req_clusterKey) {
                echo "<p>Invalid cluster key";
                $badCluster = true;
            }
        }
    }
} else {
    $badCluster = true;
}
if ($badCluster) {
    ?>
    <form enctype="multipart/form-data" action="manage.php" method="POST">
        <p>Enter cluster ID:
        <input type="text" name="clusterID" />
        <p>Cluster key:
        <input type="text" name="clusterKey" />
        <input type="submit" value="Set" />
    </form>
    <?php
} else {
    setcookie("clusterID", $req_clusterID, time() + 60*60*24*365, "/");
    setcookie("clusterKey", $req_clusterKey, time() + 60*60*24*365, "/");
}

?>
