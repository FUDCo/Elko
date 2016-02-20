<?php
require("cryptoblob.php");

/* Setup */

$keyin = '6BbTwO9qkRw7WdfE3ZXz6g==';
echo "key: " . $keyin . "\n\n";

$cryptor = new Cryptoblob($keyin);

/* Decryption test */

echo "*** Decryption Test ***\n";

$msgin = 'DRz+9EFFzO3x/QvF5D3zHAXhRVStPe3qbfDulH+gz0uA==';

$plaintextout = $cryptor->decryptMessage($msgin);

echo "message in: " . $msgin . "\n";
echo "plain out: " . $plaintextout . "\n";


/* Encryption test */

echo "\n*** Encryption Test ***\n";

$plaintextin = 'This is a somewhat longer message that will fold into multiple encryption blocks';

$ivout = null;
//$ivout = extractIV($msgin);
$msgout = $cryptor->encryptMessage($plaintextin, $ivout);

echo "plain in: " . $plaintextin . "\n";
echo "message out: " . $msgout . "\n";


/* Blob encryption test */

echo "\n*** Blob encryption test ***\n";

$obj = array();
$obj["context"] = "context-chat";
$user = array();
$user["type"] = "user";
$user["name"] = "Anon";
$obj["user"] = $user;
echo "plain object: " . json_encode($obj) . "\n";

$msgout = $cryptor->makeBlob($obj, 300);
echo "blob: " . $msgout . "\n";

?>
