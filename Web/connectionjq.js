// The connection constructor produces a connection object.
// It takes 3 parameters:
//      root           The root session url.
//      receiver(obj)  Function to be called with incoming messages. It is
//          passed the message object.  This callback will be invoked once for
//          each message received on the connection.
//      failure(str, task, err)   Function to be called when an error occurs.
//          It is passed a human-readable error message string, a task
//          identifier string indicating what it was doing or trying to do that
//          failed ("connect", "xmit", "select", or "disconnect"), and an error
//          identifier string indicating the particular problem encountered
//          (though if the problem is a JQuery XHR failure, JQuery may be
//          maddeningly non-specific here, e.g., "error").  This callback will
//          be invoked no more than once, after which the connection should be
//          regarded as broken.
//
//
// The returned connection object has two methods:
//      send(msg)      Send JSON message 'msg' to the server
//      disconnect()   Break the connection

if (typeof ELKO === 'undefined') {
    ELKO = {};
}

ELKO.connection = function (root, receiver, failure) {
     var hold = true,        // Should outgoing messages be held in the queue?
         queue = [],         // The outgoing message queue.
         sessionid,          // The session id.
         sseqnum = 1,        // The select sequence number.
         xseqnum = 1;        // The xmit sequence number.
     var disconnected = false;

     if (root.indexOf("http://") !== 0 && root.indexOf("https://") !== 0) {
         root = "http://" + root;
     }

     if ($.browser.msie && window.XDomainRequest) {
         $.ajaxTransport(function(options, originalOptions, jqXHR) {
             var xdr;
             
             return {
                 send: function(_, completeCallback) {
                     xdr = new XDomainRequest();
                     xdr.onload = function() {
                         var responses = {
                             text: xdr.responseText
                         };
                         completeCallback(200, 'success', responses);
                     };
                     xdr.onerror = xdr.ontimeout = function() {
                         var responses = {
                             text: xdr.responseText
                         };
                         completeCallback(400, 'failed', responses);
                     };
                     xdr.onprogress = function() { };
                     
                     xdr.open(options.type, options.url);
                     xdr.send(options.data);
                 },
                 abort: function() {
                     if (xdr) {
                         xdr.abort();
                     }
                 }
             };
         });
     }

     // The ask function is used to give the server the opportunity to push
     // messages.  Each message will cause the invocation of the receiver
     // function.
     function ask() {
         if (!disconnected) {
             $.ajax(root + '/select/' + sessionid + '/' + sseqnum, {
                 type: 'GET',
                 success: function(data, status, xhr) {
                     sseqnum = null;
                     var problem = null;
                     if (data) {
                         sseqnum = data.seqnum;
                         if (data.msgs && data.msgs.length) {
                             for (var i = 0; i < data.msgs.length; i += 1) {
                                 receiver(data.msgs[i]);
                             }
                         }
                         if (data.error) {
                             problem = data.error;
                         }
                         data = null;
                     }
                     if (sseqnum) {
                         if (sseqnum > 0) {
                             setTimeout(ask, 0);
                         }
                     } else {
                         problem = problem || "unknownProblem";
                         failure("select request failed, problem=" + problem,
                                 "select", problem);
                         disconnected = true;
                     }
                 },
                 error: function (xhr, status, err) {
                     failure("select request failed, status=" + status,
                             "select", status);
                     disconnected = true;
                 },
                 contentType: 'text/plain',
                 dataType: 'json',
                 processData: false,
                 crossDomain: true
             });
         }
     }

     // The post function delivers the message queue to the server.
     function post(url, success, error) {
         hold = true;
         $.ajax(url, {
             type: 'POST',
             success: success,
             error: error,
             data: queue.join('\n'),
             contentType: 'text/plain',
             dataType: 'json',
             processData: false,
             crossDomain: true
         });
         queue = [];
     }

     // The send function transmits the queue to the server. Its success
     // function will call send recursively if the queue filled up again during
     // the transmission.
     function send() {
         if (!disconnected) {
             post(root + '/xmit/' + sessionid + '/' + xseqnum,
                  function (data, status, xhr) {
                      xseqnum = null;
                      var problem = null;
                      if (data) {
                          xseqnum = data.seqnum;
                          if (data.error) {
                              problem = data.error;
                          }
                      }
                      if (xseqnum) {
                          hold = false;
                          if (queue.length) {
                              send();
                          }
                      } else {
                          problem = problem || "unknownProblem";
                          failure("xmit request failed, problem=" + problem,
                                  "xmit", problem);
                          disconnected = true;
                      }
                  },
                  function (xhr, status, err) {
                      failure("xmit request failed, status=" + status, "xmit",
                              status);
                      disconnected = true;
                  });
         }
     }

     // Start by sending a connect request.
     function connect() {
         $.ajax(root + '/connect/' + new Date().getTime(), {
             type: 'GET',
             success: function (data, status, xhr) {
                 sessionid = null;
                 var problem = null;
                 sseqnum = 1;
                 xseqnum = 1;
                 if (data) {
                     sessionid = data.sessionid;
                     if (data.error) {
                         problem = data.error;
                     }
                 }
                 if (sessionid) {
                     ask();
                     hold = false;
                     if (queue.length) {
                         send();
                     }
                 } else {
                     problem = problem || "unknownProblem";
                     failure("connect request failed, problem=" + problem,
                             "connect", problem);
                     disconnected = true;
                 }
             },
             error: function (xhr, status, err) {
                 failure("connect request failed, status=" + status, "connect",
                         status);
                 disconnected = true;
             },
             contentType: 'text/plain',
             dataType: 'json',
             processData: false,
             crossDomain: true
         });
     }
     
     connect();

     // Return the connection object containing the send and disconnect methods
     return {

         // Send the message object to the server. It will be held in the queue
         // until the xmit channel is ready.
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

         // Disconnect from the server. Send any queued messages. The
         // disconnection request is ignored if we are not currently connected.
         disconnect: function () {
             if (!disconnected && sessionid) {
                 post(root + '/disconnect/' + sessionid,
                      function (o) { },
                      function (xhr, status, err) {
                          failure("disconnect request failed, status=" +
                                  status, "disconnect", status);
                      });
                 sessionid = null;
             }
             disconnected = true;
         }
     };
};
