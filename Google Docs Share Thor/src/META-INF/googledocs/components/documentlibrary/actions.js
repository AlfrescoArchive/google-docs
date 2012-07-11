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
   
   /*
    * YUI aliases
    */
   var Dom = YAHOO.util.Dom,
      Event = YAHOO.util.Event;
   
   function loadAccountsLogo(p_obj)
   {
      // create the profile image element
      var imgEl = document.createElement("IMG");
      Dom.setAttribute(imgEl, "src", "https://accounts.google.com/CheckCookie?" + 
            "continue=https%3A%2F%2Fwww.google.com%2Fintl%2Fen%2Fimages%2Flogos%2Faccounts_logo.png&" + 
            "followup=https%3A%2F%2Fwww.google.com%2Fintl%2Fen%2Fimages%2Flogos%2Faccounts_logo.png&" + 
            "chtml=LoginDoneHtml&checkedDomains=youtube&checkConnection=youtube%3A291%3A1&" + 
            "ts=" + Date.now());
      Dom.setStyle(imgEl, "display", "none");
      Event.addListener(imgEl, "load", p_obj.onLoad.fn, p_obj.onLoad.scope, true);
      Event.addListener(imgEl, "error", p_obj.onError.fn, p_obj.onError.scope, true);
      document.body.appendChild(imgEl);
   }
   
   function onGetAuthenticationFailure(response)
   {
      destroyLoaderMessage();
      Alfresco.util.PopupManager.displayMessage( {
         text : this.msg("googledocs.actions.authentication.failure")
      });
   }
   
   // config.onLoggedIn should be on object literal of the form { fn:, scope }
   function checkGoogleLogin(config)
   {
      loadAccountsLogo.call(this, {
         onLoad:
         { 
            fn: config.onLoggedIn.fn,
            scope: config.onLoggedIn.scope
         },
         onError:
         {
            // Re-start OAuth to force the user to log in
            fn: function GDE_onLoad()
            {
               doOAuth(response.json.authURL, {
                  onComplete: {
                     fn: config.onLoggedIn.fn,
                     scope: config.onLoggedIn.scope
                  }
               });
            },
            scope: this
         }
      });
   }

   var loadingMessage = null, timerShowLoadingMessage = null, loadingMessageShowing = false;
   
   var fnShowLoadingMessage = function Googledocs_fnShowLoadingMessage(msg)
   {
      // Check the timer still exists. This is to prevent IE firing the
      // event after we cancelled it. Which is "useful".
      if (timerShowLoadingMessage)
      {
         loadingMessage = Alfresco.util.PopupManager.displayMessage({
            displayTime : 0,
            text : '<span class="wait">' + msg + '</span>',
            noEscape : true
         });

         if (YAHOO.env.ua.ie > 0)
         {
            this.loadingMessageShowing = true;
         }
         else
         {
            loadingMessage.showEvent.subscribe(
               function() {
                  this.loadingMessageShowing = true;
               }, this, true);
         }
      }
   };
   
   var destroyLoaderMessage = function Googledocs_destroyLoaderMessage()
   {
      if (timerShowLoadingMessage)
      {
         // Stop the "slow loading" timed function
         timerShowLoadingMessage.cancel();
         timerShowLoadingMessage = null;
      }

      if (loadingMessage)
      {
         if (loadingMessageShowing)
         {
            // Safe to destroy
            loadingMessage.destroy();
            loadingMessage = null;
         }
         else
         {
            // Wait and try again later
            YAHOO.lang.later(100, this, destroyLoaderMessage);
         }
      }
   };
   
   var doOAuth = function GDA_launchOAuth(authURL, config)
   {
      // basic and ugly
      // TODO Improve this
      window.showModalDialog(authURL);   
      // TODO execute a handler only when the process has completed
      config.onComplete.fn.call(config.onComplete.scope);
   };
   
   /**
    * @param config {object} must include callback object 'onComplete' as {fn: .., scope: ...}
    */
   var requestOAuthURL = function GDA_requestOAuthURL(config)
   {
      Alfresco.util.Ajax.jsonGet({
         url: Alfresco.constants.PROXY_URI + 'googledocs/authurl?state=' + Alfresco.constants.PROXY_URI + "&override=true",
         dataObj : {},
         successCallback: {
            fn: function(response) {
               doOAuth(response.json.authURL, {
                  onComplete: {
                     fn: config.onComplete.fn,
                     scope: config.onComplete.scope
                  }
               });
            },
            scope: this
         },
         failureCallback: {
            fn: onLoadAuthenticationFailure,
            scope: this
         }
      });
   };
   
   var _request = function DGA__request(config)
   {
      var success = config.successCallback;
          
      var failure =
      {
         fn: function(response)
         {
            if (response.serverResponse.status == 502) // Remote authentication has failed
            {
               requestOAuthURL({
                  onComplete: {
                     fn: function() {
                        _request.call(this, config); // Recursively call outer function
                     },
                     scope: this
                  }
               });
            }
            else
            {
               config.failureCallback.call(config.failureCallback.scope);
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
    * @method createContent
    * @static
    * 
    * @param contentType {string} e.g. "presentation"
    */
   var createContent = function Googledocs_createContent(record, contentType)
   {
      _request.call(this, {
         url: Alfresco.constants.PROXY_URI + 'googledocs/createContent',
         dataObj: {
            contenttype: contentType,
            parent: record.nodeRef
         },
         successCallback: {
            fn: function(response)
            {
               loadingMessageShowing = true;
               destroyLoaderMessage();
               window.location = window.location.protocol + "//" + window.location.host + Alfresco.constants.URL_PAGECONTEXT + "site/" + Alfresco.constants.SITE + "/googledocsEditor?nodeRef=" + response.json.nodeRef; 
            },
            scope : this
         },
         failureCallback: {
            fn: function() {
               destroyLoaderMessage();
               Alfresco.util.PopupManager.displayMessage({
                  text: this.msg("create-content.googledocs." + contentType + ".failure")
               });
            },
            scope: this
         }
      });
   };

   /**
    * Get the Google authentication status from the repo and do some work based on the result. If not authenticated an attempt will be made to reconnect.
    * 
    * @method getAuthenticationStatus
    * @static
    * 
    * @param config {object} must have property 'onAuthenticated' { fn, scope }
    */
   var getAuthenticationStatus = function getAuthenticationStatus(config)
   {
      Alfresco.util.Ajax.jsonGet({
         url: Alfresco.constants.PROXY_URI + "googledocs/authurl",
         dataObj: {
            "state": Alfresco.constants.PROXY_URI,
            "override": "true"
         },
         successCallback:
         {
            fn: function(response)
            {
               var authURL = response.json.authURL;
               if (!response.json.authenticated)
               {
                  doOAuth(authURL, {
                     onComplete: {
                        fn: function() {
                           getAuthenticationStatus.call(this); // Recursively call outer function
                        }
                     }
                  });
                  doWork.call(this);
               }
               else
               {
                  config.onAuthenticated.fn.call(config.onAuthenticated.scope);
               }
            }
         },
         failureCallback: {
            fn: onGetAuthenticationFailure,
            scope: this
         }
      });
   };

   /**
    * @method createGoogleDoc
    * @static
    * 
    * @param contentType {string} e.g. "presentation"
    */
   var createGoogleDoc = function createGoogleDoc(record, contentType)
   {
      destroyLoaderMessage.call(this);
      timerShowLoadingMessage = YAHOO.lang.later(0, this, fnShowLoadingMessage, [this.msg("create-content.googledocs." + contentType + ".creating")]);

      getAuthenticationStatus.call(this, {
         onAuthenticated: {
            fn: function() {
               checkGoogleLogin.call(this, {
                  onLoggedIn: {
                     fn: function() {
                        createContent.call(this, record, contentType);
                     }
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
         
         destroyLoaderMessage.call(this);
         timerShowLoadingMessage = YAHOO.lang.later(0, this, fnShowLoadingMessage, [this.msg("googledocs.actions.editing")]);
         
         var editDocument = function Googledocs_editDocument() {
            _request.call(this, {
               url: Alfresco.constants.PROXY_URI + 'googledocs/uploadContent',
               dataObj: {
                  nodeRef: record.nodeRef
               },
               successCallback: {
                  fn : function(response){
                     loadingMessageShowing = true;
                     destroyLoaderMessage();
                     window.location = window.location.protocol + "//" + window.location.host + Alfresco.constants.URL_PAGECONTEXT + "site/" + Alfresco.constants.SITE + "/googledocsEditor?nodeRef=" + response.json.nodeRef; 
                  },
                  scope : this
             },
               failureCallback: {
                  fn: function() {
                     destroyLoaderMessage();
                     Alfresco.util.PopupManager.displayMessage({
                        text : this.msg("googledocs.actions.editing.failure")
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
                     loadingMessageShowing = true;
                     destroyLoaderMessage();
                     this.destroy();  
                  },
                  isDefault: true
               }]
            });
         };
         
         var checkConversion = function Googledocs_checkConversion(){
            
            var success = {
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
            
            var failure = {
                  fn : function(response){
                     loadingMessageShowing = true;
                     destroyLoaderMessage();
                     Alfresco.util.PopupManager.displayMessage( {
                              text : this.msg("googledocs.actions.exportable.check.failure")
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
         
         getAuthenticationStatus.call(this, {
            onAuthenticated: {
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
         
         destroyLoaderMessage.call(this);
         timerShowLoadingMessage = YAHOO.lang.later(0, this, fnShowLoadingMessage, [this.msg("googledocs.actions.resume")]);
         
         getAuthenticationStatus.call(this, {
            onAuthenticated: {
               fn: function() {
                  checkGoogleLogin.call(this, {
                     onLoggedIn: {
                        fn: function() {
                           window.location = window.location.protocol + "//" + window.location.host + Alfresco.constants.URL_PAGECONTEXT+"site/"+Alfresco.constants.SITE + "/googledocsEditor?nodeRef=" + record.nodeRef;
                        }
                     }
                  });
               },
               scope: this
            }
         });
      }
   }),   
   
   //Start Create Content Actions
   
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
   
})();