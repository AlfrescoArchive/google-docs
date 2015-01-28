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
 * @author jottley
 */
(function () {
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
     * Toolbar constructor.
     *
     * @param {String}
     *            htmlId The HTML id of the parent element
     * @return {Alfresco.GoogleDocs.Toolbar} The new Toolbar instance
     * @constructor
     */
    Alfresco.GoogleDocs.Toolbar = function (htmlId) {
        Alfresco.GoogleDocs.Toolbar.superclass.constructor.call(this, "Alfresco.GoogleDocs.Toolbar", htmlId, ["button"]);

        /*
         * Decoupled event listeners
         */
        YAHOO.Bubbling.on('editorLoaded', this.onEditorLoaded, this);
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
            options: {
                /**
                 * Repository nodeRef of the document being edited
                 *
                 * @property nodeRef
                 * @type string
                 * @default ""
                 */
                nodeRef: "",

                /**
                 * Site URL name
                 *
                 * @property site
                 * @type String
                 * @default ""
                 */
                site: "",

                /**
                 * Whether the repository content item is versioned or not
                 *
                 * @property isVersioned
                 * @type boolean
                 * @default true
                 */
                isVersioned: true,

                /**
                 * Version of the current document, if versioned
                 *
                 * @property version
                 * @type String
                 * @default ""
                 */
                version: true
            },

            /**
             * Event handler called when "onReady"
             *
             * @method: onReady
             */
            onReady: function GDT_onReady() {
                // Return button is always available; others are dependent on the
                // user being logged into Google
                YAHOO.util.Event.addListener(this.id + "-googledocs-back-button", "click", this.onReturnClick, this, true);
            },

            /**
             * Return to the previous page. The current content is not saved back
             * and will remain in Google Docs.
             *
             * @method onReturnClick
             * @param e
             *            {object} Click event object
             */
            onReturnClick: function GDT_onReturnClick(e) {
                YAHOO.util.Event.preventDefault(e);

                var me = this; // 'this' gets broken by button handlers, need to
                // use alias

                var success =
                {
                    fn: function GDT_returnSuccess(response) {
                        this._navigateForward();
                    },
                    scope: this
                };

                var failure =
                {
                    fn: function GDT_returnFailure(response) {
                        if (response.serverResponse.status == 404) {
                            this.destroy(); // Remove the confirmation dialog

                            var success =
                            {
                                fn: function GDT_discardSuccess(response) {
                                    window.location.href = Alfresco.util.uriTemplate("userdashboardpage",
                                        {
                                            userid: encodeURIComponent(Alfresco.constants.USERNAME)
                                        });
                                },
                                scope: this
                            };

                            var failure =
                            {
                                fn: function GDT_discardFailure(response) {
                                    if (response.serverResponse.status != 403) // Access
                                    // Denied
                                    // warning
                                    // will
                                    // be
                                    // swallowed
                                    {
                                        Alfresco.GoogleDocs.showMessage({
                                            text: me.msg("googledocs.actions.discard.failure"),
                                            displayTime: 2.5,
                                            showSpinner: false
                                        });
                                    }
                                },
                                scope: this
                            };

                            var actionUrl = Alfresco.constants.PROXY_URI + "googledocs/discardContent";

                            Alfresco.GoogleDocs.showMessage({
                                text: me.msg("googledocs.accessDenied.text"),
                                displayTime: 0,
                                showSpinner: true
                            });

                            Alfresco.GoogleDocs.request({
                                url: actionUrl,
                                method: "POST",
                                dataObj: {
                                    nodeRef: me.options.nodeRef,
                                    override: true
                                },
                                successCallback: success,
                                failureCallback: failure
                            });
                        }
                        else {
                            Alfresco.util.PopupManager.displayPrompt(
                                {
                                    title: me.msg("googledocs.error.title"),
                                    text: me.msg("googledocs.error.text"),
                                    noEscape: true,
                                    buttons: [
                                        {
                                            text: me.msg("button.ok"),
                                            handler: function submitDiscard() {
                                                // Close the confirmation pop-up
                                                Alfresco.GoogleDocs.hideMessage();
                                                this.destroy();
                                            },
                                            isDefault: true

                                        }]
                                });
                        }
                    },
                    scope: this
                };

                if (Alfresco.constants.SITE !== "") {
                    var actionUrl = Alfresco.constants.PROXY_URI + "api/sites/" + Alfresco.constants.SITE +
                        "/memberships/" + Alfresco.constants.USERNAME;

                    Alfresco.GoogleDocs.request({
                        url: actionUrl,
                        dataObj: {},
                        successCallback: success,
                        failureCallback: failure
                    });
                }
                else {
                    this._navigateForward();
                }
            },


            /**
             *
             */
            onCloseClick: function GDT_onCloseClick(e) {
                YAHOO.util.Event.preventDefault(e);

                var me = this,
                    actionUrl = Alfresco.constants.PROXY_URI;

                var success =
                {
                    fn: function GDT_closeSuccess(response) {
                        if (response.json.isLatestRevision) {
                            var _success =
                            {
                                fn: function cleanup(response) {
                                    if (response.json.success) {
                                        me._navigateForward();
                                    }
                                    else {
                                        Alfresco.GoogleDocs.showMessage({
                                            text: me.msg("googledocs.actions.close.failure"),
                                            displayTime: 2.5,
                                            showSpinner: false
                                        });
                                    }
                                }
                            };

                            var _failure =
                            {
                                fn: function error(response) {
                                    Alfresco.GoogleDocs.showMessage({
                                        text: me.msg("googledocs.actions.close.failure"),
                                        displayTime: 2.5,
                                        showSpinner: false
                                    });
                                }
                            };

                            var removeContentUrl = Alfresco.constants.PROXY_URI + "googledocs/removeContent";

                            Alfresco.GoogleDocs.request({
                                url: removeContentUrl,
                                method: "POST",
                                dataObj: {
                                    nodeRef: me.options.nodeRef,
                                    force: false
                                },
                                successCallback: _success,
                                failureCallback: _failure
                            });

                        }
                        else {
                            Alfresco.util.PopupManager.displayPrompt(
                                {
                                    title: me.msg("googledocs.dialog.unsaved.title"),
                                    text: me.msg("googledocs.dialog.unsaved.message"),
                                    noEscape: true,
                                    close: true,
                                    buttons: [
                                        {
                                            text: me.msg("googledocs.button.saveTo"),
                                            handler: function submitSave() {
                                                // Close the confirmation pop-up
                                                Alfresco.GoogleDocs.hideMessage();
                                                me.onSaveToClick(this);
                                                this.destroy();
                                            },
                                            isDefault: true
                                        },
                                        {
                                            text: me.msg("googledocs.button.discard"),
                                            handler: function submitDiscard() {
                                                // Close the confirmation pop-up
                                                Alfresco.GoogleDocs.hideMessage();
                                                me.onDiscardClick(this);
                                                this.destroy();
                                            },
                                            isDefault: false
                                        }]
                                });

                            Alfresco.GoogleDocs.hideMessage();
                            this.destroy();
                        }
                    },
                    scope: this
                };

                var failure =
                {
                    fn: function GDT_closeFailure(response) {
                        if (response.serverResponse.status == 403) // Access Denied
                        // warning
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
                                                this.destroy();
                                                window.location.href = Alfresco.util.uriTemplate("userdashboardpage",
                                                    {
                                                        userid: encodeURIComponent(Alfresco.constants.USERNAME)
                                                    });
                                            },
                                            isDefault: true
                                        }]
                                });
                        }
                        else {
                            Alfresco.GoogleDocs.showMessage({
                                text: me.msg("googledocs.actions.close.failure"),
                                displayTime: 2.5,
                                showSpinner: false
                            });
                        }
                    },
                    scope: this
                };

                Alfresco.GoogleDocs.showMessage({
                    text: me.msg("googledocs.actions.close"),
                    displayTime: 0,
                    showSpinner: true
                });


                Alfresco.GoogleDocs.requestOAuthURL.call(this, {
                    onComplete: {
                        fn: function () {
                            Alfresco.GoogleDocs.checkGoogleLogin.call(this, {
                                onLoggedIn: {
                                    fn: function () {
                                        var revisionCheckUrl = Alfresco.constants.PROXY_URI + "googledocs/isLatestRevision?nodeRef=";

                                        Alfresco.GoogleDocs.request({
                                            url: revisionCheckUrl + me.options.nodeRef,
                                            method: "GET",
                                            dataObj: {},
                                            successCallback: success,
                                            failureCallback: failure
                                        });
                                    },
                                    scope: this
                                }
                            });
                        },
                        scope: this
                    }
                });
            },

            /**
             * Discard the current content item. It will be removed from Google docs
             * and the user will be returned to the content item in Share.
             *
             * @method onDiscardClick
             * @param e
             *            {object} Click event object
             */
            onDiscardClick: function GDT_onDiscardClick(e) {
                YAHOO.util.Event.preventDefault(e);

                var me = this; // 'this' gets broken by button handlers, need to use
                               // alias

                var discardContent = function GDT_discardContent() {
                    this.destroy(); // Remove the confirmation dialog

                    var success =
                    {
                        fn: function GDT_discardSuccess(response) {
                            me._navigateForward();
                        },
                        scope: this
                    };

                    var failure =
                    {
                        fn: function GDT_discardSuccess(response) {
                            if (response.serverResponse.status == 409) {
                                Alfresco.util.PopupManager.displayPrompt({
                                    title: me.msg("googledocs.concurrentEditors.title"),
                                    text: me.msg("googledocs.concurrentEditors.text"),
                                    noEscape: true,
                                    buttons: [
                                        {
                                            text: me.msg("button.ok"),
                                            handler: function submitDiscard() {
                                                this.destroy();
                                                Alfresco.GoogleDocs.showMessage({
                                                    text: me.msg("googledocs.actions.discard"),
                                                    displayTime: 0,
                                                    showSpinner: true
                                                });

                                                Alfresco.util.Ajax.jsonPost({
                                                    url: actionUrl,
                                                    dataObj: {
                                                        nodeRef: me.options.nodeRef,
                                                        override: true
                                                    },
                                                    successCallback: success,
                                                    failureCallback: failure
                                                });
                                            }
                                        },
                                        {
                                            text: me.msg("button.cancel"),
                                            handler: function cancelDiscard() {
                                                Alfresco.GoogleDocs.hideMessage();
                                                this.destroy();
                                            },
                                            isDefault: true
                                        }]
                                });
                            }
                            else if (response.serverResponse.status == 403) // Access
                            // Denied
                            // warning
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
                                                    this.destroy();
                                                    window.location.href = Alfresco.util.uriTemplate("userdashboardpage",
                                                        {
                                                            userid: encodeURIComponent(Alfresco.constants.USERNAME)
                                                        });
                                                },
                                                isDefault: true
                                            }]
                                    });
                            }
                            else {
                                Alfresco.GoogleDocs.showMessage({
                                    text: me.msg("googledocs.actions.discard.failure"),
                                    displayTime: 2.5,
                                    showSpinner: false
                                });
                            }
                        },
                        scope: this
                    };

                    var actionUrl = Alfresco.constants.PROXY_URI + "googledocs/discardContent";

                    Alfresco.GoogleDocs.showMessage({
                        text: me.msg("googledocs.actions.discard"),
                        displayTime: 0,
                        showSpinner: true
                    });

                    Alfresco.GoogleDocs.request({
                        url: actionUrl,
                        method: "POST",
                        dataObj: {
                            nodeRef: me.options.nodeRef
                        },
                        successCallback: success,
                        failureCallback: failure
                    });
                };

                Alfresco.GoogleDocs.requestOAuthURL.call(this, {
                    onComplete: {
                        fn: function () {
                            Alfresco.GoogleDocs.checkGoogleLogin.call(this, {
                                onLoggedIn: {
                                    fn: function () {
                                        Alfresco.util.PopupManager.displayPrompt(
                                            {
                                                title: this.msg("googledocs.actions.discard.warning.title"),
                                                text: this.msg("googledocs.actions.discard.warning.text"),
                                                noEscape: false,
                                                buttons: [
                                                    {
                                                        text: this.msg("button.ok"),
                                                        handler: discardContent
                                                    },
                                                    {
                                                        text: this.msg("button.cancel"),
                                                        handler: function cancelDiscard() {
                                                            this.destroy();
                                                        },
                                                        isDefault: true
                                                    }]
                                            });
                                    },
                                    scope: this
                                }
                            });
                        },
                        scope: this
                    }
                });
            },

            /**
             * Save the current content in Google Docs back the repository and
             * return the user to the content item in Share
             *
             * @method onSaveToClick
             * @param e
             *            {object} Click event object
             */
            onSaveToClick: function GDT_onSaveToClick(e) {
                var me = this, // 'this' gets broken by button handlers, need to use
                // alias
                    actionUrl = Alfresco.constants.PROXY_URI + "googledocs/saveContent";

                this.saveDiscardConfirmed = false;

                var success =
                {
                    fn: function GDT_saveSuccess(response) {
                        me._navigateForward();
                    },
                    scope: this
                };

                var failure =
                {
                    fn: function GDT_saveFailure(response) {


                        if (response.serverResponse.status == 409) // Concurrent
                        // editors warning
                        {
                            Alfresco.util.PopupManager.displayPrompt(
                                {
                                    title: me.msg("googledocs.concurrentEditors.title"),
                                    text: me.msg("googledocs.concurrentEditors.text"),
                                    noEscape: true,
                                    buttons: [
                                        {
                                            text: me.msg("button.ok"),
                                            handler: function submitDiscard() {
                                                // Close the confirmation pop-up
                                                this.destroy();
                                                if (me.configDialog) {
                                                    // Set the override form field value
                                                    Dom.get(me.configDialog.id + "-override").value = "true";
                                                    // Re-submit the form
                                                    me.configDialog.widgets.okButton.fireEvent("click", {});
                                                }
                                                else {
                                                    // Assume POST needed without form (node not
                                                    // versioned)
                                                    me.saveDiscardConfirmed = true;
                                                    // Redo the POST
                                                    Alfresco.util.Ajax.jsonPost({
                                                        url: actionUrl,
                                                        dataObj: {
                                                            nodeRef: me.options.nodeRef,
                                                            override: me.saveDiscardConfirmed
                                                        },
                                                        successCallback: success,
                                                        failureCallback: failure
                                                    });
                                                }
                                            }
                                        },
                                        {
                                            text: me.msg("button.cancel"),
                                            handler: function cancelSave() {
                                                this.destroy();
                                            },
                                            isDefault: true
                                        }]
                                });
                        }
                        else if (response.serverResponse.status == 419) // Invalid
                        // Filename
                        // warning
                        {
                            Alfresco.util.PopupManager.displayPrompt(
                                {
                                    title: me.msg("googledocs.invalidFilename.title"),
                                    text: me.msg("googledocs.invalidFilename.text"),
                                    noEscape: true,
                                    buttons: [
                                        {
                                            text: me.msg("button.ok"),
                                            handler: function submitDiscard() {
                                                // Close the confirmation pop-up
                                                Alfresco.GoogleDocs.hideMessage();
                                                this.destroy();
                                            },
                                            isDefault: true
                                        }]
                                });
                        }
                        else if (response.serverResponse.status == 403) // Access Denied
                        // warning
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
                                                this.destroy();
                                                window.location.href = Alfresco.util.uriTemplate("userdashboardpage",
                                                    {
                                                        userid: encodeURIComponent(Alfresco.constants.USERNAME)
                                                    });
                                            },
                                            isDefault: true
                                        }]
                                });
                        }
                        else {
                            if (response.serverResponse.status == 502 && me.versionDialog) // 502s
                            // may be returned here if the POST call is made by the dialog (and is not wrapped)
                            {
                                Alfresco.GoogleDocs.requestOAuthURL({
                                    onComplete: {
                                        fn: function () {
                                            // Re-submit the form
                                            me.versionDialog.widgets.okButton.fireEvent("click", {});
                                        },
                                        scope: this
                                    },
                                    override: true
                                });
                            }
                            else {
                                Alfresco.GoogleDocs.showMessage({
                                    text: me.msg("googledocs.actions.saving.failure"),
                                    displayTime: 2.5,
                                    showSpinner: false
                                });
                            }
                        }
                    },
                    scope: this
                };

                var callback = {
                    success: function (o) {
                        success.fn(o);
                    },
                    failure: function (o) {
                        failure.fn(o);
                    }
                };

                // Button event handlers for SimpleDialog
                var handleSubmit = function () {

                    Alfresco.GoogleDocs.showMessage({
                        text: me.msg("googledocs.actions.saving"),
                        displayTime: 0,
                        showSpinner: true
                    });

                    YAHOO.util.Connect.asyncRequest('POST', actionUrl, callback, JSON.stringify(this.getData()));
                    this.hide();
                };
                var handleCancel = function () {
                    this.cancel();
                };

                // main
                Alfresco.GoogleDocs.requestOAuthURL.call(this, {
                    onComplete: {
                        fn: function () {
                            Alfresco.GoogleDocs.checkGoogleLogin.call(this, {
                                onLoggedIn: {
                                    fn: function () {
                                        if (this.options.isVersioned) {
                                            if (!this.versionDialog) {
                                                this.versionDialog = new YAHOO.widget.SimpleDialog(this.id + "-new-version-dialog", {
                                                    postmethod: "manual",
                                                    close: false,
                                                    draggable: false,
                                                    effect: null,
                                                    modal: true,
                                                    visible: false,
                                                    context: [this.id + "-googledocs-saveTo-button", "tr", "tr", ["beforeShow", "windowResize"], [0, 43]],
                                                    width: "30em",
                                                    zIndex: 250,
                                                    buttons: [{text: me.msg("button.ok"), handler: handleSubmit, isDefault: true},
                                                        {text: me.msg("button.cancel"), handler: handleCancel}]
                                                });

                                                this.versionDialog.render();

                                            }

                                            var majorVersion, minorVersion;

                                            var majorMinor = this.options.version.split(".");
                                            if (majorMinor.length == 2) {
                                                // Set the version label in the dialog
                                                minorVersion = majorMinor[0] + "." + (parseInt(majorMinor[1]) + 1),
                                                    majorVersion = "" + (parseInt(majorMinor[0]) + 1) + ".0";
                                            }

                                            this.versionDialog.setBody('<div id="' + this.id +
                                            '-dialog" class="google-docs-version"><div class="hd">' + me.msg("label.header") +
                                            '</div><div class="bd"><form id="' + this.id +
                                            '-form" action="' + actionUrl +
                                            '" method="POST"><div id="' + this.id +
                                            '-versionSection-div"><div class="yui-gd"><div class="yui-u first"><span>' + me.msg("label.version") +
                                            '</span></div><div class="yui-u"><input id="' + this.id +
                                            '-minorVersion-radioButton" type="radio" name="majorVersion" value="false" checked="checked" tabindex="0"/><label for="' + this.id +
                                            '-minorVersion-radioButton" id="' + this.id +
                                            '-minorVersion">' + me.msg("label.minorVersion", minorVersion) +
                                            '</label></div></div><div class="yui-gd"><div class="yui-u first">&nbsp;</div><div class="yui-u"><input id="' + this.id +
                                            '-majorVersion-radioButton" type="radio" name="majorVersion" value="true" tabindex="0"/><label for="' + this.id +
                                            '-majorVersion-radioButton" id="' + this.id +
                                            '-majorVersion">' + me.msg("label.majorVersion", majorVersion) +
                                            '</label></div></div><div class="yui-gd"><div class="yui-u first"><label for="' + this.id +
                                            '-description-textarea">' + me.msg("label.comments") +
                                            '</label></div><div class="yui-u"><textarea id="' + this.id +
                                            '-description-textarea" name="description" cols="80" rows="4" tabindex="0"></textarea></div></div></div><div class="bdft"><input id="' + this.id +
                                            '-nodeRef" type="hidden" name="nodeRef" value="' + this.options.nodeRef + '" /><input id="' + this.id +
                                            '-override" type="hidden" name="override" value="false" /></div></form></div></div>');

                                            this.versionDialog.show();
                                        }
                                        else {
                                            Alfresco.GoogleDocs.showMessage({
                                                text: this.msg("googledocs.actions.saving"),
                                                displayTime: 0,
                                                showSpinner: true
                                            });


                                            Alfresco.GoogleDocs.request({
                                                url: actionUrl,
                                                method: "POST",
                                                dataObj: {
                                                    nodeRef: this.options.nodeRef,
                                                    override: this.saveDiscardConfirmed
                                                },
                                                successCallback: success,
                                                failureCallback: failure
                                            });
                                        }
                                    },
                                    scope: this
                                }
                            });
                        },
                        scope: this
                    }
                });
            },

            /**
             * Save the current content in Google Docs back the repository and
             * return the user to the content item in Share
             *
             * @method onSaveClick
             * @param e
             *            {object} Click event object
             */
            onSaveClick: function GDT_onSaveClick(e) {
                var me = this, // 'this' gets broken by button handlers, need to use
                // alias
                    actionUrl = Alfresco.constants.PROXY_URI + "googledocs/saveContent";

                this.saveDiscardConfirmed = false;


                if (!this.messages) {
                    var messages = new YAHOO.widget.Overlay(this.id + "-messages", {
                        close: false,
                        draggable: false,
                        visible: false,
                        height: "42px",
                        width: "100%",
                        zIndex: 1000,
                        context: [this.id + "-body", "tl", "tl", ["beforeShow", "windowResize"]]
                    });

                    messages.render();
                }

                var fadeout = function () {
                    messages.cfg.setProperty("effect", {effect: YAHOO.widget.ContainerEffect.FADE, duration: 2.0});
                    messages.hide();
                }

                var success =
                {
                    fn: function GDT_saveSuccess(response) {
                        me.options.version = response.json.version;

                        messages.setBody('<div class="gdmessage gdsave-message alfresco-share">' + me.msg("googledocs.actions.async.saved") + '</div>');
                        setTimeout(fadeout, 2000);

                        this.destroy();
                    },
                    scope: this
                };

                var failure =
                {
                    fn: function GDT_saveFailure(response) {
                        if (response.serverResponse.status == 419) // Invalid Filename warning
                        {
                            messages.setBody('<div class="gdmessage gdfailure-message alfresco-share">' + me.msg("googledocs.invalidFilename.text") +
                            '<div id="' + this.id + '-googledocs-button-close" class="gd-button-close"></div></div>');


                            YAHOO.util.Event.addListener(this.id + "-googledocs-button-close", "click", fadeout);
                        }
                        else if (response.serverResponse.status == 403) // Access Denied warning
                        {
                            messages.setBody('<div class="gdmessage gdfailure-message alfresco-share">' + me.msg("googledocs.accessDenied.text") +
                            '<div id="' + this.id + '-googledocs-button-close" class="gd-button-close"></div></div>');

                            YAHOO.util.Event.addListener(this.id + "-googledocs-button-close", "click", function (e) {
                                fadeout;

                                window.location.href = Alfresco.util.uriTemplate("userdashboardpage",
                                    {
                                        userid: encodeURIComponent(Alfresco.constants.USERNAME)
                                    });
                            });
                        }
                        else {
                            messages.setBody('<div class="gdmessage gdfailure-message alfresco-share">' + me.msg("googledocs.actions.async.failed") +
                            '<div id="' + this.id + '-googledocs-button-close" class="gd-button-close"></div></div>');

                            YAHOO.util.Event.addListener(this.id + "-googledocs-button-close", "click", fadeout);
                        }
                    },
                    scope: this
                };

                Alfresco.GoogleDocs.requestOAuthURL.call(this, {
                    onComplete: {
                        fn: function () {
                            Alfresco.GoogleDocs.checkGoogleLogin.call(this, {
                                onLoggedIn: {
                                    fn: function () {

                                        messages.setBody('<div class="gdmessage gdsaving-message alfresco-share">' + me.msg("googledocs.actions.async.saving") + '</div>');
                                        messages.show();

                                        Alfresco.GoogleDocs.request({
                                            url: actionUrl,
                                            method: "POST",
                                            dataObj: {
                                                nodeRef: this.options.nodeRef,
                                                override: true,
                                                removeFromDrive: false,
                                                majorVersion: false,
                                                description: ""
                                            },
                                            successCallback: success,
                                            failureCallback: failure
                                        });
                                    },
                                    scope: this
                                }
                            });
                        },
                        scope: this
                    }
                });
            },

            /**
             * Displays the corresponding details page for the current node
             *
             * @method _navigateForward
             * @private
             */
            _navigateForward: function GDT__navigateForward() {
                /* Was the return page specified */
                var returnPath = Alfresco.util.getQueryStringParameter("return", location.hash.replace("#", ""));
                if (returnPath) {
                    returnPath = returnPath.replace(/\?file=[^&#]*/, ""); // remove
                    // the
                    // 'file'
                    // querystring
                    // param,
                    // which
                    // causes a
                    // file to
                    // be
                    // highlighted
                    window.location.href = location.protocol + "//" + location.host + Alfresco.constants.URL_PAGECONTEXT + returnPath;
                }
                /*
                 * Did we come from the document library? If so, then direct the
                 * user back there
                 */
                else if (document.referrer.match(/documentlibrary([?]|$)/) || document.referrer.match(/repository([?]|$)/)) {
                    /*
                     * Send the user back to the last page - this could be either the
                     * document list or document details page
                     *
                     * We could use window.history.back(), but that does not trigger the
                     * document actions and metadata to be reloaded
                     */
                    window.location.href = document.referrer;
                }
                else {
                    // go forward to the appropriate details page for the node
                    window.location.href = $siteURL("document-details?nodeRef=" + this.options.nodeRef);
                }
            },

            /**
             * Decoupled event listener for editor loaded
             *
             * @method onEditorLoaded
             */
            onEditorLoaded: function GDT_onEditorLoaded(layer, args) {
                YAHOO.util.Event.addListener(this.id + "-googledocs-discard-button", "click", this.onDiscardClick, this, true);
                YAHOO.util.Event.addListener(this.id + "-googledocs-close-button", "click", this.onCloseClick, this, true);
                YAHOO.util.Event.addListener(this.id + "-googledocs-saveTo-button", "click", this.onSaveToClick, this, true);
                YAHOO.util.Event.addListener(this.id + "-googledocs-save-button", "click", this.onSaveClick, this, true);
            }


        });

})();
