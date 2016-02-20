// The connection constructor produces a connection object.
// It takes 3 parameters:
//      socketURL      The URL of the socket to connect to.
//      receiver(obj)  Function to be called with incoming messages. It is
//          passed the message object.  This callback will be invoked once for
//          each message received on the connection.
//      failure(str, task, err)   Function to be called when an error occurs.
//          It is passed a human-readable error message string, a task
//          identifier string indicating what it was doing or trying to do that
//          failed ("connect", "xmit", "select", or "disconnect"), and an error
//          identifier string indicating the particular problem encountered.
//          This callback will be invoked no more than once, after which the
//          connection should be regarded as broken.
//
//
// The returned connection object has two methods:
//      send(msg)      Send JSON message 'msg' to the server
//      disconnect()   Break the connection

if (typeof ELKO === 'undefined') {
    ELKO = {};
}

ELKO.connection = function (socketURL, receiver, failure) {
     var hold = true,        // Should outgoing messages be held in the queue?
         queue = [];         // The outgoing message queue.
     var disconnected = false;
     var socket = null;

     // Transmit the queue to the server.
     function send() {
         if (!disconnected) {
             try {
                 socket.send(queue.join('\n'));
                 queue = [];
             } catch (e) {
                 failure("WebSocket send failure", "send", e);
                 disconnect = true;
             }
         }
     }

     // Upon connection, send any queued messages
     function onOpen() {
         hold = false;
         if (queue.length) {
             send();
         }
     }

     function onClose(evt) {
         disconnected = true;
     }

     function onMessage(msg) {
         receiver(JSON.parse(msg.data))
     }

     function onError(err) {
         failure("Websocket error", "any", err);
     }

     if (socketURL.indexOf("http://") === 0) {
         socketURL = "ws://" + socketURL.substring(7);
     } else if (socketURL.indexOf("ws://") !== 0) {
         socketURL = "ws://" + socketURL;
     }
     socket = new WebSocket(socketURL);
     socket.onopen = onOpen;
     socket.onmessage = onMessage;
     socket.onclose = onClose;
     socket.onerror = onError;

     return {
         send: function (message) {
             if (message) {
                 if (typeof message !== 'string') {
                     message = JSON.stringify(message);
                 }
                 queue.push(message + '\n');
                 if (!hold) {
                     setTimeout(send, 0);
                 }
             }
         },

         disconnect: function () {
             socket.close();
             disconnected = true;
         }
     };
};
