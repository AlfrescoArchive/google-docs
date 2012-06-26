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
         YAHOO.util.Event.addListener(this.id + "-googledocs-back-button", "click", this.onReturnClick);
         YAHOO.util.Event.addListener(this.id + "-googledocs-discard-button", "click", this.onDiscardClick, this, true);
         YAHOO.util.Event.addListener(this.id + "-googledocs-save-button", "click", this.onSaveClick, this, true);
      },
      
      /**
       * Return to the previous page. The current content is not saved back and will remain in Google Docs.
       * 
       * @method onReturnClick
       * @param e {object} Click event object
       */
      onReturnClick: function GDT_onReturnClick(e)
      {
         YAHOO.util.Event.preventDefault(e);
         window.history.back();
      },

      /**
       * Discard the current content item. It will be removed from Google docs and the user will be returned
       * to the content item in Share.
       * 
       * @method onDiscardClick
       * @param e {object} Click event object
       */
      onDiscardClick: function GDT_onDiscardClick(e)
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

      /**
       * Save the current content in Google Docs back the repository and return the user to the content
       * item in Share
       * 
       * @method onSaveClick
       * @param e {object} Click event object
       */
      onSaveClick: function GDT_onSaveClick(e)
      {
         var loadingMessage = null, timerShowLoadingMessage = null, loadingMessageShowing = false, me = this;
         
         var fnShowLoadingMessage = function GDT_fnShowLoadingMessage()
         {
            // Check the timer still exists. This is to prevent IE firing the
            // event after we cancelled it. Which is "useful".
            if (timerShowLoadingMessage)
            {
               loadingMessage = Alfresco.util.PopupManager.displayMessage( {
                  displayTime : 0,
                  text : '<span class="wait">' + Alfresco.util.encodeHTML(this.msg("googledocs.actions.saving")) + '</span>',
                  noEscape : true
               });

               if (YAHOO.env.ua.ie > 0)
               {
                  this.loadingMessageShowing = true;
               }
               else
               {
                  loadingMessage.showEvent.subscribe(function() {
                        this.loadingMessageShowing = true;
                     }, this, true);
               }
            }
         };
         
         var destroyLoaderMessage = function GDT_destroyLoaderMessage()
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
                  // Wait and try again later. Scope doesn't get set correctly
                  // with "this"
                  YAHOO.lang.later(100, me, destroyLoaderMessage);
               }
            }
         };
         
         var success =
         {
            fn: function GDT_saveSuccess(response) {
               loadingMessageShowing = true;
               destroyLoaderMessage();
               window.history.back();

               // TODO WA Better to set window.location? window.history.back() seems to used cached page
               //window.location = window.location.href.replace('googledocsEditor', 'document-details');
            },
            scope : this
         };
         
         var failure =
         {
            fn: function GDT_saveFailure(response) {

               destroyLoaderMessage();
               Alfresco.util.PopupManager.displayMessage({
                  text : this.msg("googledocs.actions.saving.failure")
               });
            },
            scope : this
         };
         
         var actionUrl = Alfresco.constants.PROXY_URI + "googledocs/saveContent";

         destroyLoaderMessage();
         
         if (this.options.isVersioned)
         {
            if (!this.configDialog)
            {
               this.configDialog = new Alfresco.module.SimpleDialog(this.id + "-configDialog").setOptions(
               {
                  width: "30em",
                  templateUrl: Alfresco.constants.URL_SERVICECONTEXT + "modules/googledocs/create-new-version",
                  actionUrl: actionUrl,
                  onSuccess: success,
                  onFailure: failure,
                  doSetupFormsValidation:
                  {
                     fn: function GDT_doSetupForm_callback(form)
                     {
                        // Set the nodeRef form field value from the local setting
                        Dom.get(this.configDialog.id + "-nodeRef").value = this.options.nodeRef;
                     },
                     scope: this
                  },
                  doBeforeFormSubmit:
                  {
                     fn: function GDT_doBeforeVersionFormSubmit()
                     {
                        this.configDialog.widgets.okButton.set("disabled", true);
                        this.configDialog.widgets.cancelButton.set("disabled", true);
                        timerShowLoadingMessage = YAHOO.lang.later(0, this, fnShowLoadingMessage);
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
               });
            }
            this.configDialog.show();
         }
         else
         {
            timerShowLoadingMessage = YAHOO.lang.later(0, this, fnShowLoadingMessage);
            Alfresco.util.Ajax.jsonPost({
               url: actionUrl,
               dataObj: {
                  nodeRef: this.options.nodeRef
               },
               successCallback: success,
               failureCallback: failure
            });
         }
      }
      
   });
   
}) ();