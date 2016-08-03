#!/bin/bash
mongo elko --shell <<ENDSCRIPT
use elko
db.dropDatabase();
ENDSCRIPT
