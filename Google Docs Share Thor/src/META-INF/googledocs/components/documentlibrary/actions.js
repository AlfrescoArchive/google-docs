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
   
   var $html = Alfresco.util.encodeHTML;

   YAHOO.Bubbling.fire("registerAction", {
      actionName : "onGoogledocsActionEdit",
      fn : function dlA_onGoogledocsActionEdit(record) {

         var loadingMessage = null, timerShowLoadingMessage = null, loadingMessageShowing = false, me = this;
         
         var fnShowLoadingMessage = function Googledocs_fnShowLoadingMessage() {
            // Check the timer still exists. This is to prevent IE firing the
            // event after we cancelled it. Which is "useful".
            if (timerShowLoadingMessage) {
               loadingMessage = Alfresco.util.PopupManager.displayMessage( {
                        displayTime : 0,
                        text : '<span class="wait">' + $html(this.msg("googledocs.actions.editing")) + '</span>',
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
         
         var conversionWarning = function Googledocs_conversionWarning(conversion){
            
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
                  fn : function(response){
                     if (response.json.export_action != "default"){
                        conversionWarning(response.json.export_action);
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
         
         var editDocument = function Googledocs_editDocument(){
            destroyLoaderMessage();
         };
         
         
         var success = {
               fn : function(response){
                  if (!response.json.authenticated){

                     loadingMessageShowing = true;
                     destroyLoaderMessage();
                     
                     // basic and ugly
                    window.showModalDialog(response.json.authURL);   
                  }
                  
                  checkConversion();
                  
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
            url : Alfresco.constants.PROXY_URI + 'googledocs/authurl?state='+Alfresco.constants.PROXY_URI,
            dataObj : {},
            successCallback : success,
            failureCallback : failure
         });
         
         
      }
   }),
   
   YAHOO.Bubbling.fire("registerAction", {
      actionName : "onGoogledocsActionCreateDocument",
      fn : function dlA_onGoogledocsActionCreateDocument(record) {

         var loadingMessage = null, timerShowLoadingMessage = null, loadingMessageShowing = false, me = this;
         
         var fnShowLoadingMessage = function Googledocs_fnShowLoadingMessage() {
            // Check the timer still exists. This is to prevent IE firing the
            // event after we cancelled it. Which is "useful".
            if (timerShowLoadingMessage) {
               loadingMessage = Alfresco.util.PopupManager.displayMessage( {
                        displayTime : 0,
                        text : '<span class="wait">' + $html(this.msg("create-content.googledocs.document.creating")) + '</span>',
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
         
         var createDocument = function Googledocs_createDocument(){
            destroyLoaderMessage();
         };
         
         
         var success = {
               fn : function(response){
                  if (!response.json.authenticated){

                     loadingMessageShowing = true;
                     destroyLoaderMessage();
                     
                     // basic and ugly
                    window.showModalDialog(response.json.authURL);   
                  }
                  
                  alert(record.nodeRef);
                  
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
            url : Alfresco.constants.PROXY_URI + 'googledocs/authurl?state='+Alfresco.constants.PROXY_URI,
            dataObj : {},
            successCallback : success,
            failureCallback : failure
         });
         
         
      }
   }),
   
   YAHOO.Bubbling.fire("registerAction", {
      actionName : "onGoogledocsActionCreateSpreadsheet",
      fn : function dlA_onGoogledocsActionCreateSpreadsheet(record) {

         var loadingMessage = null, timerShowLoadingMessage = null, loadingMessageShowing = false, me = this;
         
         var fnShowLoadingMessage = function Googledocs_fnShowLoadingMessage() {
            // Check the timer still exists. This is to prevent IE firing the
            // event after we cancelled it. Which is "useful".
            if (timerShowLoadingMessage) {
               loadingMessage = Alfresco.util.PopupManager.displayMessage( {
                        displayTime : 0,
                        text : '<span class="wait">' + $html(this.msg("create-content.googledocs.spreadsheet.creating")) + '</span>',
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
         
         var createSpreadsheet = function Googledocs_createSpreadsheet(){
            destroyLoaderMessage();
         };
         
         
         var success = {
               fn : function(response){
                  if (!response.json.authenticated){

                     loadingMessageShowing = true;
                     destroyLoaderMessage();
                     
                     // basic and ugly
                    window.showModalDialog(response.json.authURL);   
                  }
                  
                  alert(record.nodeRef);
                  
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
            url : Alfresco.constants.PROXY_URI + 'googledocs/authurl?state='+Alfresco.constants.PROXY_URI,
            dataObj : {},
            successCallback : success,
            failureCallback : failure
         });
         
         
      }
   }),
   
   YAHOO.Bubbling.fire("registerAction", {
      actionName : "onGoogledocsActionCreatePresentation",
      fn : function dlA_onGoogledocsActionCreatePresentation(record) {

         var loadingMessage = null, timerShowLoadingMessage = null, loadingMessageShowing = false, me = this;
         
         var fnShowLoadingMessage = function Googledocs_fnShowLoadingMessage() {
            // Check the timer still exists. This is to prevent IE firing the
            // event after we cancelled it. Which is "useful".
            if (timerShowLoadingMessage) {
               loadingMessage = Alfresco.util.PopupManager.displayMessage( {
                        displayTime : 0,
                        text : '<span class="wait">' + $html(this.msg("create-content.googledocs.presentation.creating")) + '</span>',
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
         
         var createPresentation = function Googledocs_createPresentation(){
            destroyLoaderMessage();
         };
         
         
         var success = {
               fn : function(response){
                  if (!response.json.authenticated){

                     loadingMessageShowing = true;
                     destroyLoaderMessage();
                     
                     // basic and ugly
                    window.showModalDialog(response.json.authURL);   
                  }
                  
                  alert(record.nodeRef);
                  
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
            url : Alfresco.constants.PROXY_URI + 'googledocs/authurl?state='+Alfresco.constants.PROXY_URI,
            dataObj : {},
            successCallback : success,
            failureCallback : failure
         });
         
         
      }
   })
   
})();