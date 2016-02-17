/**
 * Copyright (C) 2005-2015 Alfresco Software Limited.
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

/**
 * Google Docs utility functions
 * 
 * @author jottley
 * @author wabson
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
   Alfresco.GoogleDocs.hideMessage = function GDA_hideMessage()
   {
      if (userMessage)
      {
         if (userMessage.element)
         {
            userMessage.destroy();
         }
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
   Alfresco.GoogleDocs.showMessage = function GDA_showMessage(config)
   {
      if (userMessageText != config.text) // only update if text has changed
      {
         Alfresco.GoogleDocs.hideMessage();
         var displayTime = (config.displayTime === null || typeof config.displayTime == "undefined") ? 0 : config.displayTime,
               showSpinner = (config.showSpinner === null || typeof config.showSpinner == "undefined") ? true : config.showSpinner;
         userMessage = Alfresco.util.PopupManager.displayMessage({
            displayTime: displayTime,
            text: showSpinner ? '<span class="wait">' + config.text + '</span>' : config.text,
            noEscape: true,
            modal: true
         });
         userMessageText = config.text;
      }
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
   Alfresco.GoogleDocs.loadAccountsLogo = function GDA_loadAccountsLogo(config)
   {
      // create the profile image element
      var imgEl = document.createElement("IMG");
      Dom.setAttribute(imgEl, "src", "https://accounts.google.com/CheckCookie?" + 
            "continue=https%3A%2F%2Fwww.google.com%2Fintl%2Fen%2Fimages%2Flogos%2Faccounts_logo.png&" + 
            "followup=https%3A%2F%2Fwww.google.com%2Fintl%2Fen%2Fimages%2Flogos%2Faccounts_logo.png&" + 
            "chtml=LoginDoneHtml&checkedDomains=youtube&checkConnection=youtube%3A291%3A1&" + 
            "ts=" + new Date().getTime());
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
   Alfresco.GoogleDocs.checkGoogleLogin = function GDA_checkGoogleLogin(config)
   {
      Alfresco.GoogleDocs.loadAccountsLogo.call(this, {
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
               Alfresco.GoogleDocs.requestOAuthURL({
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
   Alfresco.GoogleDocs.doOAuth = function GDA_launchOAuth(authURL, config)
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
               
               /*
                * Modal dialogs do not work properly in IE, they are unable to navigate to cross-domain URL such as the return page,
                * causing a second popup window to be opened which then does not close
                * 
                * See https://issues.alfresco.com/jira/browse/GOOGLEDOCS-100
                */
               if (typeof window.showModalDialog == "function" && !YAHOO.env.ua.ie)
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

       var elements = Dom.getElementsByClassName('yui-button', 'span', 'prompt');
       Dom.addClass(elements[0], 'alf-primary-button');
      
      Alfresco.GoogleDocs.hideMessage.call(this);
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
   Alfresco.GoogleDocs.requestOAuthURL = function GDA_requestOAuthURL(config)
   {
      var me = this;

       if (Alfresco.logger.isDebugEnabled())
      {
         Alfresco.logger.debug("Checking Google authorization status");
         Alfresco.logger.debug("Override status: " + config.override);
      }
      Alfresco.util.Ajax.jsonGet({
         url: Alfresco.constants.PROXY_URI + "googledocs/authurl",
         dataObj : {
            state: Alfresco.constants.PROXY_URI,
            override: "true",
            nodeRef: config.nodeRef || ""
         },
         successCallback: {
            fn: function(response) {

                if (response.json.status != null ) {
                    if (response.json.status.code == 401)
                    {
                        Alfresco.util.PopupManager.displayPrompt(
                            {
                                title: me.msg("googledocs.accessDenied.title"),
                                text: me.msg("googledocs.accessDenied.text"),
                                noEscape: true,
                                buttons: [
                                    {
                                        text: me.msg("button.ok"),
                                        handler: function submitDiscard() {
                                            // Close the confirmation pop-up
                                            Alfresco.GoogleDocs.hideMessage();
                                            this.hide();
                                            //Send the user to the login page
                                            Alfresco.GoogleDocs.request({
                                                url: Alfresco.constants.URL_PAGECONTEXT + "dologout",
                                                method: "POST"
                                            });
                                        },
                                        isDefault: true
                                    }]
                            });
                    }
                }
                else
                {
                    if (Alfresco.logger.isDebugEnabled()) {
                        Alfresco.logger.debug("Authorized: " + response.json.authenticated);
                    }
                    if (!response.json.authenticated || config.override == true) {
                        if (Alfresco.logger.isDebugEnabled()) {
                            Alfresco.logger.debug("Authorizing using URL: " + response.json.authURL);
                        }
                        // TODO Must pass authurl response through here
                        Alfresco.GoogleDocs.doOAuth(response.json.authURL, {
                            onComplete: {
                                fn: config.onComplete.fn.bind(config.onComplete.scope, response),
                                scope: config.onComplete.scope
                            }
                            /*onComplete: {
                             fn: function() {
                             Alfresco.GoogleDocs.requestOAuthURL.call(this); // Recursively call outer function
                             }
                             }*/
                        });
                    }
                    else {
                        config.onComplete.fn.call(config.onComplete.scope, response);
                    }
                }
            },
            scope: this
         },
         failureCallback: {
            fn: function() {
               Alfresco.GoogleDocs.showMessage({
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
    * @method request
    * @static
    * 
    * @param config {object} object literal containing success and failure callbacks
    *          - successCallback: object literal of form { fn: ..., scope: ... } to be executed when the request succeeds
    *          - failureCallback: object literal of form { fn: ..., scope: ... } to be executed when the request succeeds
    */
   Alfresco.GoogleDocs.request = function GDA_request(config)
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
               Alfresco.GoogleDocs.requestOAuthURL({
                  onComplete: {
                     fn: function() {
                        Alfresco.GoogleDocs.request.call(this, config); // Recursively call outer function
                     },
                     scope: this
                  },
                  override: true
               });
            }
            else
            {
               config.failureCallback.fn.call(config.failureCallback.scope, response);
            }
         },
         scope: this
      };
       
      Alfresco.util.Ajax.jsonRequest({
         url: config.url,
         method: config.method || "GET",
         dataObj: config.dataObj,
         requestContentType: config.requestContentType || null,
         successCallback: success,
         failureCallback: failure
      });
   };

   /**
    * OAuth return handler - used for returning from OAuth popup.
    * 
    * @method onOAuthReturn
    * @static
    * @default null
    */
   Alfresco.GoogleDocs.onOAuthReturn = null;
   
})();
