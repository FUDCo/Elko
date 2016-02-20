<?php

require_once("launcherfuncs.php");

if (isset($argv[3])) {
    $componentTag = $argv[3];
}

LaunchGen::genLaunch($argv[1], $argv[2], $componentTag);

?>
