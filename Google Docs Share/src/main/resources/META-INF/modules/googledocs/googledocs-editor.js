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

/**
 * Google Docs Editor component
 * 
 * @namespace Alfresco.GoogleDocs
 * @class Alfresco.GoogleDocs.Editor
 * @author wabson
 */
(function()
{
   // Ensure the namespace exists
   Alfresco.GoogleDocs = Alfresco.GoogleDocs || {};
   
   /**
    * YUI Library aliases
    */
   var Dom = YAHOO.util.Dom;

   /**
    * Alfresco Slingshot aliases
    */
   var $html = Alfresco.util.encodeHTML,
      $siteURL = Alfresco.util.siteURL;

   /**
    * Editor constructor.
    * 
    * @param {String} htmlId The HTML id of the parent element
    * @return {Alfresco.GoogleDocs.Editor} The new Editor instance
    * @constructor
    */
   Alfresco.GoogleDocs.Editor = function(htmlId)
   {
      Alfresco.GoogleDocs.Editor.superclass.constructor.call(this, "Alfresco.GoogleDocs.Editor", htmlId, ["button"]);
   };

   /**
    * Extend Alfresco.component.Base
    */
   YAHOO.extend(Alfresco.GoogleDocs.Editor, Alfresco.component.Base,
   {

      /**
       * Object container for initialization options
       *
       * @property options
       * @type object
       */
      options:
      {
         /**
          * Repository nodeRef of the document being edited
          * 
          * @property nodeRef
          * @type String
          * @default ""
          */
         nodeRef: "",
         
         /**
          * Google docs editor URL
          * 
          * @property editorURL
          * @type String
          * @default true
          */
         editorURL: ""
      },

      /**
       * Event handler called when "onReady"
       *
       * @method: onReady
       */
      onReady: function GDE_onReady()
      {
         Alfresco.GoogleDocs.checkGoogleLogin.call(this, {
            onLoggedIn: {
               fn: function() {
                  Dom.get(this.id + "-gdocs-wrapper").innerHTML = "<iframe class=\"gdocs-embed\" src=\"" + this.options.editorURL + "\"></iframe>";
                  
                  // Notify the toolbar that we are done, so it can enable its buttons
                  YAHOO.Bubbling.fire ('editorLoaded', {});
               },
               scope: this
            }
         });
         // TODO Deal with editorURL being empty?
      },
      
      /**
       * Authenticate to Google Docs using OAuth flow
       * 
       * @method onLoginClick
       * @param e {object} Click event object
       */
      onLoginClick: function GDE_onLoginClick(e)
      {
         YAHOO.util.Event.preventDefault(e);
         
        var loadingMessage = null, timerShowLoadingMessage = null, loadingMessageShowing = false, me = this;
         
         var fnShowLoadingMessage = function Googledocs_fnShowLoadingMessage() {
            // Check the timer still exists. This is to prevent IE firing the
            // event after we cancelled it. Which is "useful".
            if (timerShowLoadingMessage) {
               loadingMessage = Alfresco.util.PopupManager.displayMessage( {
                        displayTime : 0,
                        text : '<span class="wait">' + this.msg("googledocs.actions.editing") + '</span>',
                        noEscape : true
                     });

               if (YAHOO.env.ua.ie > 0) {
                  this.loadingMessageShowing = true;
               } else {
                  loadingMessage.showEvent.subscribe(
                              function() {
                                 this.loadingMessageShowing = true;
                              }, this, true);
               }
            }
         };
         
         var destroyLoaderMessage = function Googledocs_destroyLoaderMessage() {
            if (timerShowLoadingMessage) {
               // Stop the "slow loading" timed function
               timerShowLoadingMessage.cancel();
               timerShowLoadingMessage = null;
            }

            if (loadingMessage) {
               if (loadingMessageShowing) {
                  // Safe to destroy
                  loadingMessage.destroy();
                  loadingMessage = null;
               } else {
                  // Wait and try again later. Scope doesn't get set correctly
                  // with "this"
                  YAHOO.lang.later(100, me, destroyLoaderMessage);
               }
            }
         };
         
         destroyLoaderMessage();
         timerShowLoadingMessage = YAHOO.lang.later(0, this, fnShowLoadingMessage);
         
         var success = {
               fn : function(response){
                     loadingMessageShowing = true;
                     destroyLoaderMessage();
                     
                     // basic and ugly
                    window.showModalDialog(response.json.authURL);   

                    loggedIn();
                  
               },
               scope : this
         };
         
         var failure = {
               fn : function(response) {

                  destroyLoaderMessage();
                  Alfresco.util.PopupManager.displayMessage( {
                           text : this.msg("googledocs.actions.authentication.failure")
                        });

               },
               scope : this
         };
         
         Alfresco.util.Ajax.jsonGet( {
            url : Alfresco.constants.PROXY_URI + 'googledocs/authurl?state='+Alfresco.constants.PROXY_URI+"&override=true",
            dataObj : {},
            successCallback : success,
            failureCallback : failure
         });
      }
      
   });
   
}) ();