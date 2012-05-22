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
   
   var back = function(e){
      YAHOO.util.Event.preventDefault(e);
      window.history.back();
   }
   
   var discard = function(e){
      //YAHOO.util.Event.preventDefault(e);
      
      /*Alfresco.util.PopupManager.displayPrompt(
            {
               title: 'Discard Changes',
               text: 'Clicking the Discard button, will revert the content back to the original state in Alfresco.  ie. delete content from google docs.  New content place holders will be removed from the repository.',
               noEscape: false,
               buttons: [
               {
                  text: Alfresco.util.message("button.yes", this.name),
                  handler: function discardChanges()
                  {
                     me.promptActive = false;
                     this.destroy();
                  }
               },
               {
                  text: Alfresco.util.message("button.no", this.name),
                  handler: function cancelDiscard()
                  {
                     me.promptActive = false;
                     this.destroy();  
                  },
                  isDefault: true
               }]
            }); */
      
      alert("Clicking the Discard button, will revert the content back to the original state in Alfresco.  ie. delete content from google docs.  New content place holders will be removed from the repository.");
   }
   
   var save = function(e){
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
      
      destroyLoaderMessage();
      timerShowLoadingMessage = YAHOO.lang.later(0, this, fnShowLoadingMessage);
      
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
      
      Alfresco.util.Ajax.jsonGet( {
         url : Alfresco.constants.PROXY_URI + 'googledocs/saveContent?nodeRef='+QueryString.nodeRef,
         dataObj : {},
         successCallback : success,
         failureCallback : failure
      });
      
      
   }
   
   YAHOO.util.Event.addListener("googledocs-back-button", "click", back);
   YAHOO.util.Event.addListener("googledocs-discard-button", "click", discard);
   YAHOO.util.Event.addListener("googledocs-save-button", "click", save);
   
}) ();