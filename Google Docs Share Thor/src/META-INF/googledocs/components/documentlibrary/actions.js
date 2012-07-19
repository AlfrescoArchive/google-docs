/**
 * Copyright (C) 2005-2012 Alfresco Software Limited.
 * 
 * This file is part of Alfresco
 * 
 * Alfresco is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 * 
 * Alfresco is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */

(function() {
   
   /**
    * Google Docs namespace
    */
   Alfresco.GoogleDocs = Alfresco.GoogleDocs || {};
   
   /*
    * YUI aliases
    */
   var Dom = YAHOO.util.Dom,
      Event = YAHOO.util.Event;

   /*
    * Static user-displayed message, timer and status
    */
   var userMessage = null, userMessageText = "";
   
   /*
    * OAuth dialog width and height in pixels
    */
   var OAUTH_WINDOW_WIDTH = 480, OAUTH_WINDOW_HEIGHT = 480;
   
   /**
    * Destroy the message displayed to the user
    * 
    * @method hideMessage
    * @static
    */
   var hideMessage = function GDA_hideMessage()
   {
      if (userMessage)
      {
         userMessage.destroy();
         userMessage = null;
         userMessageText = "";
      }
   };
   
   /**
    * Remove any existing popup message and show a new message
    * 
    * @method showMessage
    * @static
    * @param config {object} object literal containing success callback
    *          - text {String} The message text to display
    *          - displayTime {int} Display time in seconds. Defaults to zero, i.e. show forever
    *          - showSpinner {boolean} Whether to display the spinner image or not, default is true
    */
   var showMessage = function GDA_showMessage(config)
   {
      if (userMessageText != config.text) // only update if text has changed
      {
         hideMessage();
         var displayTime = (config.displayTime === null || typeof config.displayTime == "undefined") ? 0 : config.displayTime,
               showSpinner = (config.showSpinner === null || typeof config.showSpinner == "undefined") ? true : config.showSpinner;
         userMessage = Alfresco.util.PopupManager.displayMessage({
            displayTime: displayTime,
            text: showSpinner ? '<span class="wait">' + config.text + '</span>' : config.text,
            noEscape: true
         });
         userMessageText = config.text;
      }
   }
   
   /**
    * Forward the browser to the editing page for the specified repository nodeRef
    * 
    * @param nodeRef {String} NodeRef of the item being edited
    * @returns null
    */
   var navigateToEditorPage = function GDA_navigateToEditorPage(nodeRef)
   {
      window.location = window.location.protocol + "//" + window.location.host + Alfresco.constants.URL_PAGECONTEXT + 
         "site/" + Alfresco.constants.SITE + "/googledocsEditor?nodeRef=" + nodeRef;
   };
   
   /**
    * Insert the Google accounts logo into the Dom with event handlers, in order for us to detect whether or not the user
    * is currently logged into Google or not
    * 
    * @function loadAccountsLogo
    * @static
    * @param config {object} object literal containing success callback
    *          - onLoad: object literal of form { fn: ..., scope: ... } to be executed if the image loads correctly (i.e. user is logged in)
    *          - onError: object literal of form { fn: ..., scope: ... } to be executed if the image loads with errors (i.e. user not logged in)
    * 
    */
   var loadAccountsLogo = function GDA_loadAccountsLogo(config)
   {
      // create the profile image element
      var imgEl = document.createElement("IMG");
      Dom.setAttribute(imgEl, "src", "https://accounts.google.com/CheckCookie?" + 
            "continue=https%3A%2F%2Fwww.google.com%2Fintl%2Fen%2Fimages%2Flogos%2Faccounts_logo.png&" + 
            "followup=https%3A%2F%2Fwww.google.com%2Fintl%2Fen%2Fimages%2Flogos%2Faccounts_logo.png&" + 
            "chtml=LoginDoneHtml&checkedDomains=youtube&checkConnection=youtube%3A291%3A1&" + 
            "ts=" + Date.now());
      Dom.setStyle(imgEl, "display", "none");
      Event.addListener(imgEl, "load", config.onLoad.fn, config.onLoad.scope, true);
      Event.addListener(imgEl, "error", config.onError.fn, config.onError.scope, true);
      document.body.appendChild(imgEl);
   };

   /**
    * Perform some work, checking first that the user is logged in to Google in the client. If they are not logged in then
    * an attempt is made to force them to re-login by prompting them to run through the OAuth process again, before doing the
    * work.
    * 
    * @function checkGoogleLogin
    * @static
    * @param config {object} object literal containing success callback
    *          - onLoggedIn: object literal of form { fn: ..., scope: ... } to be executed if the user is logged in to Google
    * 
    */
   var checkGoogleLogin = function GDA_checkGoogleLogin(config)
   {
      loadAccountsLogo.call(this, {
         onLoad:
         {
            fn: function GDA_checkGoogleLogin_onLoad()
            {
               if (Alfresco.logger.isDebugEnabled() )
               {
                  Alfresco.logger.debug("Google accounts logo loaded successfully. Continuing.");
               }
               config.onLoggedIn.fn.call(config.onLoggedIn.scope);
            },
            scope: this
         },
         onError:
         {
            // Re-start OAuth to force the user to log in
            fn: function GDA_checkGoogleLogin_onError()
            {
               if (Alfresco.logger.isDebugEnabled() )
               {
                  Alfresco.logger.debug("Google accounts logo loaded with errors. Re-requesting OAuth.");
               }
               requestOAuthURL({
                  onComplete: {
                     fn: config.onLoggedIn.fn,
                     scope: config.onLoggedIn.scope
                  },
                  override: true
               });
            },
            scope: this
         }
      });
   };
   
   /**
    * Start the OAuth process in the client. A popup window is opened using the supplied URL, which should present an appropriate
    * authorization screen, and hand back control to the parent window.
    * 
    * When control is received back again the callback supplied in the config will be executed
    * 
    * @method doOAuth
    * @static
    * 
    * @param authURL {String} URL to use in the popup to start authorization
    * @param config {object} object literal containing success callback
    *          - onComplete: object literal of form { fn: ..., scope: ... } to be executed when OAuth has completed
    */
   var doOAuth = function GDA_launchOAuth(authURL, config)
   {
      var returnFn = function(result)
      {
         if (result)
         {
            // execute the handler only when the process has completed
            config.onComplete.fn.call(config.onComplete.scope);
         }
      };
      
      /* 
       * Throw up a prompt to the user telling them they need to reauthorize Alfresco. This prepares them for 
       * the OAuth popup and allows us to launch the new window without this getting blocked by the browser.
       */
      Alfresco.util.PopupManager.displayPrompt(
      {
         title: Alfresco.util.message("title.googleDocs.reAuthorize"),
         text: Alfresco.util.message("text.googleDocs.reAuthorize"),
         noEscape: true,
         buttons: [
         {
            text: Alfresco.util.message("button.ok", this.name),
            handler: function GDA_doOAuth_okHandler()
            {
               this.destroy();
               
               Alfresco.GoogleDocs.onOAuthReturn = returnFn;
               
               if (typeof window.showModalDialog == "function")
               {
                  var returnVal = window.showModalDialog(authURL, {onOAuthReturn: returnFn}, "dialogwidth:" + OAUTH_WINDOW_WIDTH + ";dialogheight:" + OAUTH_WINDOW_HEIGHT); // only returns on popup close
               }
               else
               {
                  var popup = window.open(authURL, "GDOAuth", "menubar=no,location=no,resizable=no,scrollbars=yes,status=no,width=" + OAUTH_WINDOW_WIDTH + ",height=" + OAUTH_WINDOW_HEIGHT + ",modal=yes"); // returns straight away
               }
            }
         },
         {
            text: Alfresco.util.message("button.cancel", this.name),
            handler: function GDA_doOAuth_cancelHandler()
            {
               this.destroy();  
            },
            isDefault: true
         }]
      });
      
      hideMessage.call(this);
   };
   
   /**
    * Make a request to the repository to get the OAuth URL to be used to authorize against Google Docs, and start the 
    * OAuth process in the client if required. Normally authorization is performed only if not currently authorized, but
    * the override flag can be used to force re-authentication.
    * 
    * @method requestOAuthURL
    * @static
    * 
    * @param config {object} object literal containing success callback
    *          - onComplete: object literal of form { fn: ..., scope: ... } to be executed when OAuth has completed
    *          - override: {boolean} Normally OAuth will be attempted only if the repository indicates the user is not
    *             authenticated. Use this flag to force re-authorization regardless of the current state.
    */
   var requestOAuthURL = function GDA_requestOAuthURL(config)
   {
      if (Alfresco.logger.isDebugEnabled())
      {
         Alfresco.logger.debug("Checking Google authorization status");
         Alfresco.logger.debug("Override status: " + config.override);
      }
      Alfresco.util.Ajax.jsonGet({
         url: Alfresco.constants.PROXY_URI + "googledocs/authurl",
         dataObj : {
            state: Alfresco.constants.PROXY_URI,
            override: "true"
         },
         successCallback: {
            fn: function(response) {
               if (Alfresco.logger.isDebugEnabled())
               {
                  Alfresco.logger.debug("Authorized: " + response.json.authenticated);
               }
               if (!response.json.authenticated || config.override == true)
               {
                  if (Alfresco.logger.isDebugEnabled())
                  {
                     Alfresco.logger.debug("Authorizing using URL: " + response.json.authURL);
                  }
                  doOAuth(response.json.authURL, {
                     onComplete: {
                        fn: config.onComplete.fn,
                        scope: config.onComplete.scope
                     }
                     /*onComplete: {
                        fn: function() {
                           requestOAuthURL.call(this); // Recursively call outer function
                        }
                     }*/
                  });
               }
               else
               {
                  config.onComplete.fn.call(config.onComplete.scope);
               }
            },
            scope: this
         },
         failureCallback: {
            fn: function() {
               showMessage({
                  text: this.msg("googledocs.actions.authentication.failure"), 
                  displayTime: 2.5,
                  showSpinner: false
               });
            },
            scope: this
         }
      });
   };
   
   /**
    * Make a request to the repository to perform a Google Docs related action
    * 
    * The request is executed via an async call and the status of the reponse is checked. If a 502 response is returned, then
    * an attempt is made to re-authorize the repository to Google Docs via OAuth, before the request is re-tried.
    * 
    * @method _request
    * @static
    * 
    * @param config {object} object literal containing success and failure callbacks
    *          - successCallback: object literal of form { fn: ..., scope: ... } to be executed when the request succeeds
    *          - failureCallback: object literal of form { fn: ..., scope: ... } to be executed when the request succeeds
    */
   var _request = function GDA__request(config)
   {
      var success = config.successCallback;
          
      var failure =
      {
         fn: function(response)
         {
            if (response.serverResponse.status == 502) // Remote authentication has failed
            {
               if (Alfresco.logger.isDebugEnabled() )
               {
                  Alfresco.logger.debug("Google Docs request requires authorization but repository does not appear to be authorized. Re-requesting OAuth.");
               }
               requestOAuthURL({
                  onComplete: {
                     fn: function() {
                        _request.call(this, config); // Recursively call outer function
                     },
                     scope: this
                  },
                  override: true
               });
            }
            else
            {
               config.failureCallback.fn.call(config.failureCallback.scope);
            }
         },
         scope: this
      };
       
      Alfresco.util.Ajax.jsonGet({
         url: config.url,
         dataObj: config.dataObj,
         successCallback: success,
         failureCallback: failure
      });
   };
   
   /**
    * Create a new content item of the specified type in Google Docs. This method fires off a request to the repository, and will handle
    * authorization errors returned by attempting to re-authorize via OAuth.
    * 
    * After creating the item in Google Docs the user is forwarded directly to the editor page.
    * 
    * @method createContent
    * @static
    * 
    * @param record {object} Object literal representing the folder in which to create the content. Must have a 'nodeRef' property.
    * @param contentType {string} one of "document", "spreadsheet" or "presentation"
    */
   var createContent = function GDA_createContent(record, contentType)
   {
      if (Alfresco.logger.isDebugEnabled() )
      {
         Alfresco.logger.debug("Creating Google Doc of type " + contentType);
      }
      _request.call(this, {
         url: Alfresco.constants.PROXY_URI + 'googledocs/createContent',
         dataObj: {
            contenttype: contentType,
            parent: record.nodeRef
         },
         successCallback: {
            fn: function(response)
            {
               navigateToEditorPage(response.json.nodeRef);
            },
            scope : this
         },
         failureCallback: {
            fn: function() {
               showMessage({
                  text: this.msg("create-content.googledocs." + contentType + ".failure"), 
                  displayTime: 2.5,
                  showSpinner: false
               });
            },
            scope: this
         }
      });
   };

   /**
    * Delegate handler for Create Google Docs XXX actions. Defers to createContent() to perform the actual work, this function wraps createContent
    * with checks to first ensure that the current user is authorized against Google Docs and that they are logged into Google in the client.
    * 
    * @method createGoogleDoc
    * @static
    * 
    * @param record {object} Object literal representing the folder in which to create the content. Must have a 'nodeRef' property.
    * @param contentType {string} one of "document", "spreadsheet" or "presentation"
    */
   var createGoogleDoc = function createGoogleDoc(record, contentType)
   {
      var msgId = "create-content.googledocs." + contentType + ".creating";
      showMessage({
         text: this.msg("create-content.googledocs." + contentType + ".creating"), 
         displayTime: 0,
         showSpinner: true
      });

      requestOAuthURL.call(this, {
         onComplete: {
            fn: function() {
               checkGoogleLogin.call(this, {
                  onLoggedIn: {
                     fn: function() {
                        showMessage({
                           text: this.msg("create-content.googledocs." + contentType + ".creating"), 
                           displayTime: 0,
                           showSpinner: true
                        });
                        createContent.call(this, record, contentType);
                     },
                     scope: this
                  }
               });
            },
            scope: this
         }
      });
   };

   YAHOO.Bubbling.fire("registerAction", {
      actionName : "onGoogledocsActionEdit",
      fn : function dlA_onGoogledocsActionEdit(record) {
         
         var me = this;
         
         showMessage({
            text: this.msg("googledocs.actions.editing"), 
            displayTime: 0,
            showSpinner: true
         });
         
         var editDocument = function Googledocs_editDocument() {
            showMessage({
               text: this.msg("googledocs.actions.editing"), 
               displayTime: 0,
               showSpinner: true
            });
            _request.call(this, {
               url: Alfresco.constants.PROXY_URI + 'googledocs/uploadContent',
               dataObj: {
                  nodeRef: record.nodeRef
               },
               successCallback:
               {
                  fn : function(response)
                  {
                     navigateToEditorPage(response.json.nodeRef);
                  },
                  scope : this
               },
               failureCallback:
               {
                  fn: function()
                  {
                     showMessage({
                        text: this.msg("googledocs.actions.editing.failure"), 
                        displayTime: 2.5,
                        showSpinner: false
                     });
                  },
                  scope: this
               }
            });
         };
         
         var me = this, conversionWarning = function Googledocs_conversionWarning(conversion) {
            
            Alfresco.util.PopupManager.displayPrompt(
            {
               title: conversion == "upgrade" ? Alfresco.util.message("title.conversionUpgradeAction", this.name) : Alfresco.util.message("title.conversionDowngradeAction", this.name),
               text: conversion == "upgrade" ? Alfresco.util.message("label.confirmUpgradeAction", this.name) : Alfresco.util.message("label.confirmDowngradeAction", this.name),
               noEscape: true,
               buttons: [
               {
                  text: Alfresco.util.message("button.yes", this.name),
                  handler: function continueToEdit()
                  {
                     this.destroy();
                     editDocument.call(this);
                  }
               },
               {
                  text: Alfresco.util.message("button.no", this.name),
                  handler: function cancelEdit()
                  {
                     me.promptActive = false;
                     hideMessage();
                     this.destroy();  
                  },
                  isDefault: true
               }]
            });
         };
         
         var checkConversion = function Googledocs_checkConversion(){
            
            var success =
            {
               fn : function(response)
               {
                  if (response.json.export_action != "default")
                  {
                     conversionWarning.call(this, response.json.export_action);
                  }
                  else
                  {
                     editDocument.call(this);
                  }
               },
               scope : this
            };
            
            var failure =
            {
               fn : function(response)
               {
                  showMessage({
                     text: this.msg("googledocs.actions.exportable.check.failure"), 
                     displayTime: 2.5,
                     showSpinner: false
                  });
               },
               scope : this
            };
            
            Alfresco.util.Ajax.jsonGet( {
               url : Alfresco.constants.PROXY_URI + 'googledocs/exportable?mimetype='+record.node.mimetype,
               dataObj : {},
               successCallback : success,
               failureCallback : failure
            });
            
         };
         
         requestOAuthURL.call(this, {
            onComplete: {
               fn: function() {
                  checkGoogleLogin.call(this, {
                     onLoggedIn: {
                        fn: function() {
                           checkConversion.call(this)
                        },
                        scope: this
                     }
                  });
               },
               scope: this
            }
         });
      }
   }),
   
   YAHOO.Bubbling.fire("registerAction", {
      actionName : "onGoogledocsActionResume",
      fn : function dlA_onGoogledocsActionResume(record) {

         showMessage({
            text: this.msg("googledocs.actions.resume"), 
            displayTime: 0,
            showSpinner: true
         });
         
         requestOAuthURL.call(this, {
            onComplete: {
               fn: function() {
                  checkGoogleLogin.call(this, {
                     onLoggedIn: {
                        fn: function() {
                           navigateToEditorPage(record.nodeRef);
                        }
                     }
                  });
               },
               scope: this
            }
         });
      }
   }),
   
   // Start Create Content Actions
   
   YAHOO.Bubbling.fire("registerAction", {
      actionName : "onGoogledocsActionCreateDocument",
      fn: function dlA_onGoogledocsActionCreateDocument(record)
      {
         createGoogleDoc.call(this, record, "document");
      }
   }),
   
   YAHOO.Bubbling.fire("registerAction", {
      actionName : "onGoogledocsActionCreateSpreadsheet",
      fn : function dlA_onGoogledocsActionCreateSpreadsheet(record) {
         createGoogleDoc.call(this, record, "spreadsheet");
      }
   }),
   
   YAHOO.Bubbling.fire("registerAction", {
      actionName : "onGoogledocsActionCreatePresentation",
      fn : function dlA_onGoogledocsActionCreatePresentation(record)
      {
         createGoogleDoc.call(this, record, "presentation");
      }
   })
   
   Alfresco.GoogleDocs.onOAuthReturn = null;
   
})();