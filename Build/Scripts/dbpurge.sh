#!/bin/bash
mongo elko --shell mongohelper.js <<ENDSCRIPT
db.odb.drop();
ENDSCRIPT


