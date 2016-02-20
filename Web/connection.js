// The connection constructor produces a connection object.
// It takes 3 parameters:
//      root        The root session url.
//      receiver    The function to be called with incoming messages.
//                  It is passed the message object.
//      error       The function to be called when an error occurs. It is
//                  passed an object that comes either from the server or
//                  from yuc.asyncRequest.
// The returned connection object has two methods:
//      send
//      disconnect

if (typeof ELKO === 'undefined') {
    ELKO = {};
}

ELKO.connection = function (root, receiver, error) {
     var hold = true,        // Should outgoing messages be held in the queue?
         queue = [],         // The outgoing message queue.
         sessionid,          // The session id.
         sseqnum = 1,        // The select sequence number.
         xseqnum = 1,        // The xmit sequence number.
         yuc = YAHOO.util.Connect;
     var disconnected = false;

     if (root.indexOf("http://") !== 0) {
         root = "http://" + root;
     }

// The ask function is used to give the server the opportunity to push
// messages.  Each message will cause the invocation of the receiver function.

     function ask() {
         if (!disconnected) {
             yuc.asyncRequest('GET', root+'/select/' + sessionid + '/' + sseqnum, {
                 success: function (o) {
                     sseqnum = null;
                     if (o.responseText) {
                         var data = JSON.parse(o.responseText), i;
                         if (data) {
                             sseqnum = data.seqnum;
                             if (data.msgs && data.msgs.length) {
                                 for (i = 0; i < data.msgs.length; i += 1) {
                                     receiver(data.msgs[i]);
                                 }
                             }
                             data = null;
                         }
                     }
                     if (sseqnum) {
                         if (sseqnum > 0) {
                             setTimeout(ask, 0);
                         }
                     } else {
                         error(o);
                     }
                 },
                 failure: error
             });
         }
     }

// The post function delivers the message queue to the server.

     function post(url, success) {
         hold = true;
         yuc.initHeader('Content-Type', 'text/plain');
         yuc.asyncRequest('POST', url, {success: success, failure: error},
             queue.join('\n'));
         queue = [];
     }

// The send function transmits the queue to the server. Its success function
// will call send recursively if the queue filled up again during the
// transmission.

     function send() {
         post(root + '/xmit/' + sessionid + '/' + xseqnum, function (o) {
             xseqnum = null;
             if (o.responseText) {
                 var data = JSON.parse(o.responseText);
                 xseqnum = data && data.seqnum;
             }
             if (xseqnum) {
                 hold = false;
                 if (queue.length) {
                     send();
                 }
             } else {
                 error(o);
             }
         });
     }

// Start by sending a connect request.

     function connect() {
         yuc.asyncRequest('GET', root + '/connect/' + new Date().getTime(), {
             success: function (o) {
                 if (o.responseText) {
                     var data = JSON.parse(o.responseText);
                     sessionid = data && data.sessionid;
                     sseqnum = 1;
                     xseqnum = 1;
                 }
                 if (sessionid) {
                     ask();
                     hold = false;
                     if (queue.length) {
                         send();
                     }
                 } else {
                     error(o);
                 }
             },
             failure: error
         });
     }
     
     connect();

// Return the connection object containing the send and disconnect methods.

     return {

// Send the message object to the server. It will be held in the queue until
// the xmit channel is ready.

         send: function (message) {
             if (!sessionid) {
                 //connect();
             }
             if (message) {
                 if (typeof message !== 'string') {
                     message = JSON.stringify(message);
                 }
                 queue.push(message);
                 if (!hold) {
                     setTimeout(send, 0);
                 }
             }
         },

// Disconnect from the server. Send any queued messages. The disconnection
// request is ignored if we are not currently connected.

         disconnect: function () {
             if (sessionid) {
                 post(root + '/disconnect/' + sessionid, function (o) {});
                 sessionid = null;
             }
             disconnected = true;
         }
     };
};
