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
 * Google Docs Document Library actions. Defines JS actions for documents, as well as for folders via
 * the Create Content menu.
 * 
 * @author jottley
 * @author wabson
 */
(function() {
   
   /*
    * YUI aliases
    */
   var Dom = YAHOO.util.Dom,
      Event = YAHOO.util.Event;
   
   /**
    * Forward the browser to the editing page for the specified repository nodeRef
    * 
    * @param nodeRef {String} NodeRef of the item being edited
    * @returns null
    */
   var navigateToEditorPage = function GDA_navigateToEditorPage(nodeRef)
   {
      var returnPath = location.pathname.replace(Alfresco.constants.URL_PAGECONTEXT, "") + location.search + location.hash;
      Alfresco.util.navigateTo(Alfresco.util.siteURL("googledocsEditor?nodeRef=" + encodeURIComponent(nodeRef) + "&return=" + encodeURIComponent(returnPath), {
         site: Alfresco.constants.SITE
      }, true));
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
      Alfresco.GoogleDocs.request.call(this, {
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
            fn: function(response) {
                if (response.serverResponse.status == 503) {
                   Alfresco.util.PopupManager.displayPrompt(
             	   {   
             	       title: this.msg("googledocs.disabled.title"),
             	       text: this.msg("googledocs.disabled.text"),
             	       noEscape: true,
             	       buttons: [
             	       {
             	          text: this.msg("button.ok"),
             	          handler: function submitDiscard()
             	          {
             	  		     // Close the confirmation pop-up
             	  			 Alfresco.GoogleDocs.hideMessage();
             	  			 this.destroy();
             	  		   },
             	  		   isDefault: true
             	  		}]
             	    });
                }
                else 
                {           	
            	   Alfresco.GoogleDocs.showMessage({
                     text: this.msg("create-content.googledocs." + contentType + ".failure"), 
                     displayTime: 2.5,
                     showSpinner: false
                   });
                }
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
      Alfresco.GoogleDocs.showMessage({
         text: this.msg("create-content.googledocs." + contentType + ".creating"), 
         displayTime: 0,
         showSpinner: true
      });

      Alfresco.GoogleDocs.requestOAuthURL.call(this, {
         onComplete: {
            fn: function() {
               Alfresco.GoogleDocs.checkGoogleLogin.call(this, {
                  onLoggedIn: {
                     fn: function() {
                        Alfresco.GoogleDocs.showMessage({
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

   /**
    * Edit an existing document in Google Docs
    *
    * @method onGoogledocsActionEdit
    * @param record {object} Object literal representing the file or folder on which the work should be performed
    */
   YAHOO.Bubbling.fire("registerAction", {
      actionName : "onGoogledocsActionEdit",
      fn : function dlA_onGoogledocsActionEdit(record) {
         
         var me = this;
         
         Alfresco.GoogleDocs.showMessage({
            text: this.msg("googledocs.actions.editing"), 
            displayTime: 0,
            showSpinner: true
         });
         
         var editDocument = function Googledocs_editDocument() {
            Alfresco.GoogleDocs.showMessage({
               text: this.msg("googledocs.actions.editing"), 
               displayTime: 0,
               showSpinner: true
            });
            Alfresco.GoogleDocs.request.call(this, {
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
                  fn: function(response)
                  {
                     if (response.serverResponse.status == 503)
                     {
                        Alfresco.util.PopupManager.displayPrompt(
                        {   
                           title: this.msg("googledocs.disabled.title"),
                   	       text: this.msg("googledocs.disabled.text"),
                   	       noEscape: true,
                   	       buttons: [
                   	       {
                              text: this.msg("button.ok"),
                   	          handler: function submitDiscard()
                   	          {
                   	  		     // Close the confirmation pop-up
                   	  			 Alfresco.GoogleDocs.hideMessage();
                   	  			 this.destroy();
                   	  		   },
                   	  		   isDefault: true
                   	  		}]
                   	    }); 
                     }
                     else
                     {
                	    Alfresco.GoogleDocs.showMessage({
                           text: this.msg("googledocs.actions.editing.failure"), 
                           displayTime: 2.5,
                           showSpinner: false
                        });
                     }
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
                     editDocument.call(me);
                  }
               },
               {
                  text: Alfresco.util.message("button.no", this.name),
                  handler: function cancelEdit()
                  {
                     me.promptActive = false;
                     Alfresco.GoogleDocs.hideMessage();
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
                  if (response.serverResponse.status == 503)
                  {
                	  Alfresco.util.PopupManager.displayPrompt(
                      {   
                         title: this.msg("googledocs.disabled.title"),
                         text: this.msg("googledocs.disabled.text"),
                         noEscape: true,
                         buttons: [
                         {
                            text: this.msg("button.ok"),
                        	handler: function submitDiscard()
                        	{
                        	   // Close the confirmation pop-up
                        	   Alfresco.GoogleDocs.hideMessage();
                        	   this.destroy();
                        	},
                        	isDefault: true
                         }]
                      });
                  }
                  else
                  {
            	   Alfresco.GoogleDocs.showMessage({
                     text: this.msg("googledocs.actions.exportable.check.failure"), 
                     displayTime: 2.5,
                     showSpinner: false
                  });
                  }
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
         
         Alfresco.GoogleDocs.requestOAuthURL.call(this, {
            onComplete: {
               fn: function() {
                  Alfresco.GoogleDocs.checkGoogleLogin.call(this, {
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

   /**
    * Resume editing a document that is being edited in Google Docs
    *
    * @method onGoogledocsActionResume
    * @param record {object} Object literal representing the file or folder on which the work should be performed
    */
   YAHOO.Bubbling.fire("registerAction", {
      actionName : "onGoogledocsActionResume",
      fn : function dlA_onGoogledocsActionResume(record) {

         Alfresco.GoogleDocs.showMessage({
            text: this.msg("googledocs.actions.resume"), 
            displayTime: 0,
            showSpinner: true
         });
         
         Alfresco.GoogleDocs.requestOAuthURL.call(this, {
            onComplete: {
               fn: function() {
                  Alfresco.GoogleDocs.checkGoogleLogin.call(this, {
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
   
   /*
    * Create Content Actions
    */

   /**
    * Create a new Google Document file
    *
    * @method onGoogledocsActionCreateDocument
    * @param record {object} Object literal representing the file or folder on which the work should be performed
    */
   YAHOO.Bubbling.fire("registerAction", {
      actionName : "onGoogledocsActionCreateDocument",
      fn: function dlA_onGoogledocsActionCreateDocument(record)
      {
         createGoogleDoc.call(this, record, "document");
      }
   }),

   /**
    * Create a new Google Spreadsheet file
    *
    * @method onGoogledocsActionCreateSpreadsheet
    * @param record {object} Object literal representing the file or folder on which the work should be performed
    */
   YAHOO.Bubbling.fire("registerAction", {
      actionName : "onGoogledocsActionCreateSpreadsheet",
      fn : function dlA_onGoogledocsActionCreateSpreadsheet(record) {
         createGoogleDoc.call(this, record, "spreadsheet");
      }
   }),
   
   /**
    * Create a new Google Presentation file
    *
    * @method onGoogledocsActionCreatePresentation
    * @param record {object} Object literal representing the file or folder on which the work should be performed
    */
   YAHOO.Bubbling.fire("registerAction", {
      actionName : "onGoogledocsActionCreatePresentation",
      fn : function dlA_onGoogledocsActionCreatePresentation(record)
      {
         createGoogleDoc.call(this, record, "presentation");
      }
   })
   
})();