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
 * Google Docs Toolbar component
 * 
 * @namespace Alfresco.GoogleDocs
 * @class Alfresco.GoogleDocs.Toolbar
 * @author wabson
 */
(function()
{
   // Ensure the namespace exists
   Alfresco.GoogleDocs = Alfresco.GoogleDocs || {};

   /**
    * Toolbar constructor.
    * 
    * @param {String} htmlId The HTML id of the parent element
    * @return {Alfresco.GoogleDocs.Toolbar} The new Toolbar instance
    * @constructor
    */
   Alfresco.GoogleDocs.Toolbar = function(htmlId)
   {
      Alfresco.GoogleDocs.Toolbar.superclass.constructor.call(this, "Alfresco.GoogleDocs.Toolbar", htmlId, ["button"]);
   };

   /**
    * Extend Alfresco.component.Base
    */
   YAHOO.extend(Alfresco.GoogleDocs.Toolbar, Alfresco.component.Base,
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
          * @type string
          * @default ""
          */
         nodeRef: "",
         
         /**
          * Whether the repository content item is versioned or not
          * 
          * @property isVersioned
          * @type boolean
          * @default true
          */
         isVersioned: true
      },

      /**
       * Event handler called when "onReady"
       *
       * @method: onReady
       */
      onReady: function GDT_onReady()
      {
         YAHOO.util.Event.addListener(this.id + "-googledocs-back-button", "click", this.back);
         YAHOO.util.Event.addListener(this.id + "-googledocs-discard-button", "click", this.discard, this, true);
         YAHOO.util.Event.addListener(this.id + "-googledocs-save-button", "click", this.save, this, true);
      },
      
      back: function GDT_back(e)
      {
         YAHOO.util.Event.preventDefault(e);
         window.history.back();
      },
      
      discard: function GDT_discard(e)
      {
         //YAHOO.util.Event.preventDefault(e);
         
         Alfresco.util.PopupManager.displayPrompt(
         {
            title: this.msg("title.discard"),
            text: this.msg("warning.discard"),
            noEscape: false,
            buttons: [
            {
               text: this.msg("button.ok"),
               handler: function discardChanges()
               {
                  this.destroy();
               }
            },
            {
               text: this.msg("button.cancel"),
               handler: function cancelDiscard()
               {
                  this.destroy();  
               },
               isDefault: true
            }]
         });
      },
      
      saveVersion: function GDT_saveVersion()
      {
         var actionUrl = Alfresco.constants.PROXY_URI + "googledocs/saveContent";
         
         //Event.stopEvent(e);
         
         if (!this.configDialog)
         {
            this.configDialog = new Alfresco.module.SimpleDialog(this.id + "-configDialog").setOptions(
            {
               width: "30em",
               templateUrl: Alfresco.constants.URL_SERVICECONTEXT + "modules/googledocs/create-new-version", actionUrl: actionUrl,
               onSuccess:
               {
                  fn: function GDT_onConfigFeed_callback(response)
                  {
                     // Forward the user to the document details page
                     window.location = window.location.href.replace('googledocsEditor', 'document-details');
                  },
                  scope: this
               },
               doSetupFormsValidation:
               {
                  fn: function GDT_doSetupForm_callback(form)
                  {
                     // Set the nodeRef form field value from the local setting
                     Dom.get(this.configDialog.id + "-nodeRef").value = this.options.nodeRef;
                  },
                  scope: this
               }
            });
         }
         else
         {
            this.configDialog.setOptions(
            {
               actionUrl: actionUrl
            })
         }
         this.configDialog.show();
      },
      
      save: function GDT_save(e)
      {
         //YAHOO.util.Event.preventDefault(e);
         
         var loadingMessage = null, timerShowLoadingMessage = null, loadingMessageShowing = false, me = this;
         
         var fnShowLoadingMessage = function Googledocs_fnShowLoadingMessage() {
            // Check the timer still exists. This is to prevent IE firing the
            // event after we cancelled it. Which is "useful".
            if (timerShowLoadingMessage) {
               loadingMessage = Alfresco.util.PopupManager.displayMessage( {
                        displayTime : 0,
                        text : '<span class="wait">' + Alfresco.util.encodeHTML(Alfresco.util.message("googledocs.actions.saving")) + '</span>',
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
         
         //See http://stackoverflow.com/a/979995
         var QueryString = function () {
            // This function is anonymous, is executed immediately and 
            // the return value is assigned to QueryString!
            var query_string = {};
            var query = window.location.search.substring(1);
            var vars = query.split("&");
            for (var i=0;i<vars.length;i++) {
              var pair = vars[i].split("=");
                  // If first entry with this name
              if (typeof query_string[pair[0]] === "undefined") {
                query_string[pair[0]] = pair[1];
                  // If second entry with this name
              } else if (typeof query_string[pair[0]] === "string") {
                var arr = [ query_string[pair[0]], pair[1] ];
                query_string[pair[0]] = arr;
                  // If third or later entry with this name
              } else {
                query_string[pair[0]].push(pair[1]);
              }
            } 
              return query_string;
          } ();
         
         var success = {
               fn : function(response){
                  
                  loadingMessageShowing = true;
                  destroyLoaderMessage();
                  
                  window.history.back();
                  
               },
               scope : this
         };
         
         var failure = {
               fn : function(response) {

                  destroyLoaderMessage();
                  Alfresco.util.PopupManager.displayMessage( {
                           text : Alfresco.util.message("googledocs.actions.saving.failure")
                        });

               },
               scope : this
         };
         
         if (this.options.isVersioned)
         {
            this.saveVersion();
         }
         else
         {
            destroyLoaderMessage();
            timerShowLoadingMessage = YAHOO.lang.later(0, this, fnShowLoadingMessage);
            
            Alfresco.util.Ajax.jsonGet( {
               url : Alfresco.constants.PROXY_URI + 'googledocs/saveContent?nodeRef='+QueryString.nodeRef,
               dataObj : {},
               successCallback : success,
               failureCallback : failure
            });
         }
      },
      
      back: function GDT_back(e)
      {
         YAHOO.util.Event.preventDefault(e);
         window.history.back();
      }
      
   });
   
}) ();