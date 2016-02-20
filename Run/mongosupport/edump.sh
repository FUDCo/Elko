#!/bin/csh
mongo edump.js | tail +6 | sed -e 's/\t/  /g' | sed -e 's/" : /": /' | sed -e 's/"\([a-z$]*\)":/\1:/'

