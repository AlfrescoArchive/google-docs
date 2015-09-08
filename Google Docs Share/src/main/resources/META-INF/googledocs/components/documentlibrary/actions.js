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
 * Google Docs Document Library actions. Defines JS actions for documents, as well as for folders via
 * the Create Content menu.
 *
 * @author jottley
 * @author wabson
 */
(function () {

    /*
     * YUI aliases
     */
    var Dom = YAHOO.util.Dom,
        Event = YAHOO.util.Event,
        KeyListener = YAHOO.util.KeyListener;

    /**
     * Forward the browser to the editing page for the specified repository nodeRef
     *
     * @param nodeRef {String} NodeRef of the item being edited
     * @returns null
     */
    var navigateToEditorPage = function GDA_navigateToEditorPage(editorUrl) {
        var editor = window.open(decodeURI(editorUrl), '_blank');
        editor.focus();
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
    var createContent = function GDA_createContent(record, contentType) {
        if (Alfresco.logger.isDebugEnabled()) {
            Alfresco.logger.debug("Creating Google Doc of type " + contentType);
        }
        Alfresco.GoogleDocs.request.call(this, {
            url: Alfresco.constants.PROXY_URI + 'googledocs/createContent',
            dataObj: {
                contenttype: contentType,
                parent: record.nodeRef
            },
            beforeRequestCallback: {
                fn: function (response) {
                    Alfresco.GoogleDocs.showMessage({
                        text: this.msg("create-content.googledocs." + contentType + ".creating"),
                        displayTime: 0,
                        showSpinner: true
                    });
                },
                scope: this
            },
            successCallback: {
                fn: function (response) {
                    navigateToEditorPage(response.json.editorUrl);
                    location.reload();
                },
                scope: this
            },
            failureCallback: {
                fn: function (response) {
                    if (response.serverResponse.status == 503) {
                        Alfresco.util.PopupManager.displayPrompt(
                            {
                                title: this.msg("googledocs.disabled.title"),
                                text: this.msg("googledocs.disabled.text"),
                                noEscape: true,
                                buttons: [
                                    {
                                        text: this.msg("button.ok"),
                                        handler: function submitDiscard() {
                                            // Close the confirmation pop-up
                                            Alfresco.GoogleDocs.hideMessage();
                                            this.destroy();
                                        },
                                        isDefault: true
                                    }]
                            });
                    }
                    else {
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
    var createGoogleDoc = function createGoogleDoc(record, contentType) {
        var msgId = "create-content.googledocs." + contentType + ".creating";
        Alfresco.GoogleDocs.showMessage({
            text: this.msg("create-content.googledocs." + contentType + ".creating"),
            displayTime: 0,
            showSpinner: true
        });

        Alfresco.GoogleDocs.requestOAuthURL.call(this, {
            onComplete: {
                fn: function () {
                    Alfresco.GoogleDocs.checkGoogleLogin.call(this, {
                        onLoggedIn: {
                            fn: function () {
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
        actionName: "onGoogledocsActionEdit",
        fn: function dlA_onGoogledocsActionEdit(record) {

            var me = this;

            Alfresco.GoogleDocs.showMessage({
                text: this.msg("googledocs.actions.editing"),
                displayTime: 0,
                showSpinner: true
            });

            var editDocument = function Googledocs_editDocument(p_obj) {
                Alfresco.GoogleDocs.showMessage({
                    text: this.msg("googledocs.actions.editing"),
                    displayTime: 0,
                    showSpinner: true
                });
                var editData = {};
                if (p_obj.permissions) {
                    editData.permissions = p_obj.permissions;
                }
                Alfresco.GoogleDocs.request.call(this, {
                    url: Alfresco.constants.PROXY_URI + 'googledocs/uploadContent?nodeRef=' + record.nodeRef,
                    method: "POST",
                    requestContentType: Alfresco.util.Ajax.JSON,
                    dataObj: editData,
                    beforeRequestCallback: {
                        fn: function (response) {
                            Alfresco.GoogleDocs.showMessage({
                                text: this.msg("googledocs.actions.editing"),
                                displayTime: 0,
                                showSpinner: true
                            });
                        },
                        scope: this
                    },
                    successCallback: {
                        fn: function (response) {
                            navigateToEditorPage(response.json.editorUrl);
                            location.reload();
                        },
                        scope: this
                    },
                    failureCallback: {
                        fn: function (response) {
                            if (response.serverResponse.status == 503) {
                                Alfresco.util.PopupManager.displayPrompt(
                                    {
                                        title: this.msg("googledocs.disabled.title"),
                                        text: this.msg("googledocs.disabled.text"),
                                        noEscape: true,
                                        buttons: [
                                            {
                                                text: this.msg("button.ok"),
                                                handler: function submitDiscard() {
                                                    // Close the confirmation pop-up
                                                    Alfresco.GoogleDocs.hideMessage();
                                                    this.destroy();
                                                },
                                                isDefault: true
                                            }]
                                    });
                            }
                            else {
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

            var me = this, conversionWarning = function Googledocs_conversionWarning(conversion, p_obj) {

                Alfresco.util.PopupManager.displayPrompt(
                    {
                        title: conversion == "upgrade" ? Alfresco.util.message("title.conversionUpgradeAction", this.name) : Alfresco.util.message("title.conversionDowngradeAction", this.name),
                        text: conversion == "upgrade" ? Alfresco.util.message("label.confirmUpgradeAction", this.name) : Alfresco.util.message("label.confirmDowngradeAction", this.name),
                        noEscape: true,
                        buttons: [
                            {
                                text: Alfresco.util.message("button.yes", this.name),
                                handler: function continueToEdit() {
                                    this.destroy();
                                    editDocument.call(me, p_obj);
                                }
                            },
                            {
                                text: Alfresco.util.message("button.no", this.name),
                                handler: function cancelEdit() {
                                    me.promptActive = false;
                                    Alfresco.GoogleDocs.hideMessage();
                                    this.destroy();
                                },
                                isDefault: true
                            }]
                    });
            };

            var checkConversion = function Googledocs_checkConversion(p_obj) {

                var success =
                {
                    fn: function (response) {
                        if (response.json.export_action != "default") {
                            conversionWarning.call(this, response.json.export_action, p_obj);
                        }
                        else {
                            editDocument.call(this, p_obj);
                        }
                    },
                    scope: this
                };

                var failure =
                {
                    fn: function (response) {
                        if (response.serverResponse.status == 503) {
                            Alfresco.util.PopupManager.displayPrompt(
                                {
                                    title: this.msg("googledocs.disabled.title"),
                                    text: this.msg("googledocs.disabled.text"),
                                    noEscape: true,
                                    buttons: [
                                        {
                                            text: this.msg("button.ok"),
                                            handler: function submitDiscard() {
                                                // Close the confirmation pop-up
                                                Alfresco.GoogleDocs.hideMessage();
                                                this.destroy();
                                            },
                                            isDefault: true
                                        }]
                                });
                        }
                        else {
                            Alfresco.GoogleDocs.showMessage({
                                text: this.msg("googledocs.actions.exportable.check.failure"),
                                displayTime: 2.5,
                                showSpinner: false
                            });
                        }
                    },
                    scope: this
                };

                Alfresco.util.Ajax.jsonGet({
                    url: Alfresco.constants.PROXY_URI + 'googledocs/exportable?mimetype=' + record.node.mimetype,
                    dataObj: {},
                    successCallback: success,
                    failureCallback: failure
                });
            };

            Alfresco.GoogleDocs.requestOAuthURL.call(this, {
                nodeRef: record.nodeRef,
                onComplete: {
                    fn: function (authResp) { // Auth resp contains the OAuth URL to use for the doc (not needed here) and the permissions
                        Alfresco.GoogleDocs.checkGoogleLogin.call(this, {
                            onLoggedIn: {
                                fn: function () {
                                    Alfresco.GoogleDocs.getResumeSharingInstance(Alfresco.util.generateDomId()).show({
                                        nodeRef: record.nodeRef,
                                        permissions: authResp.json.permissions,
                                        onComplete: {
                                            fn: function (p_obj) {
                                                Alfresco.GoogleDocs.showMessage({
                                                    text: this.msg("googledocs.actions.editing"),
                                                    displayTime: 0,
                                                    showSpinner: true
                                                });
                                                // Resume uploading the document
                                                checkConversion.call(this, p_obj);
                                            },
                                            scope: this
                                        }
                                    })
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
     * Checkin an existing document in Google Docs
     *
     * @method onGoogledocsActionCheckin
     * @param record {object} Object literal representing the file or folder on which the work should be performed
     */
    YAHOO.Bubbling.fire("registerAction", {
        actionName: "onGoogledocsActionCheckin",
        fn: function dlA_onGoogledocsActionCheckin(record) {

            var me = this,
                saveDiscardConfirmed = false,
                actionUrl = Alfresco.constants.PROXY_URI + 'googledocs/saveContent';

            Alfresco.GoogleDocs.showMessage({
                text: this.msg("googledocs.actions.checkingin"),
                displayTime: 0,
                showSpinner: true
            });


            var success = {
                fn: function (response) {
                    location.reload();
                },
                scope: this
            };

            var failure = {
                fn: function (response) {
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
                                            if (me.versionDialog) {
                                                // Set the override form field value
                                                Dom.get(me.id + "-override").value = "true";
                                                // Re-submit the form
                                                me.versionDialog.getButtons()[0].fireEvent("click", {});
                                            }
                                            else {
                                                // Assume POST needed without form (node not
                                                // versioned)
                                                me.saveDiscardConfirmed = true;
                                                // Redo the POST
                                                Alfresco.util.Ajax.jsonPost({
                                                    url: actionUrl,
                                                    dataObj: {
                                                        nodeRef: record.nodeRef,
                                                        override: me.saveDiscardConfirmed,
                                                        removeFromDrive: true
                                                    },
                                                    successCallback: success,
                                                    failureCallback: failure
                                                });
                                            }
                                            this.hide();
                                        }
                                    },
                                    {
                                        text: me.msg("button.cancel"),
                                        handler: function cancelSave() {
                                            Alfresco.GoogleDocs.hideMessage();
                                            this.hide();
                                        },
                                        isDefault: true
                                    }]
                            });
                    }
                    else if (response.serverResponse.status == 419) // Invalid Filename warning
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
                                            this.hide();
                                        },
                                        isDefault: true
                                    }]
                            });
                    }
                    else if (response.serverResponse.status == 403) // Access Denied warning
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
                                            //Refresh the page to hopefully update what actions are available to the current user
                                            location.reload();
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
                                        me.versionDialog.getButtons()[0].fireEvent("click", {});
                                    },
                                    scope: this
                                },
                                override: true
                            });
                        }
                        else {
                            Alfresco.GoogleDocs.showMessage({
                                text: me.msg("googledocs.actions.checkin.failure"),
                                displayTime: 2.5,
                                showSpinner: false
                            });

                            //GOOGLEDOCS-305 reload the page after error message is shown.
                            window.setTimeout(
                                function()
                                {
                                    location.reload();
                                },
                                2500);
                        }
                    }
                },
                scope: this
            };

            var checkinDocument = function Googledocs_checkinDocument(p_obj) {
                this.hide();
                Alfresco.GoogleDocs.showMessage({
                    text: me.msg("googledocs.actions.checkingin"),
                    displayTime: 0,
                    showSpinner: true
                });

                Alfresco.GoogleDocs.request.call(this, {
                    url: actionUrl,
                    method: "POST",
                    requestContentType: Alfresco.util.Ajax.JSON,
                    dataObj: {
                        nodeRef: record.nodeRef,
                        override: this.getData().override,
                        removeFromDrive: true,
                        majorVersion: this.getData().majorVersion,
                        description: this.getData().description
                    },
                    beforeRequestCallback: {
                        fn: function (response) {
                            Alfresco.GoogleDocs.showMessage({
                                text: this.msg("googledocs.actions.checkingin"),
                                displayTime: 0,
                                showSpinner: true
                            });
                        },
                        scope: this
                    },
                    successCallback: success,
                    failureCallback: failure
                });
            };

            var handleCancel = function () {
                this.cancel();
                Alfresco.GoogleDocs.hideMessage();
            };

            Alfresco.GoogleDocs.requestOAuthURL.call(this, {
                nodeRef: record.nodeRef,
                onComplete: {
                    fn: function (authResp) { // Auth resp contains the OAuth URL to use for the doc (not needed here) and the permissions
                        Alfresco.GoogleDocs.checkGoogleLogin.call(this, {
                            onLoggedIn: {
                                fn: function () {
                                    if (record.jsNode.hasAspect("cm:versionable")) {
                                        if (!this.versionDialog) {
                                            this.versionDialog = new YAHOO.widget.SimpleDialog(this.id + "-new-version-dialog", {
                                                postmethod: "manual",
                                                close: false,
                                                draggable: false,
                                                effect: null,
                                                modal: true,
                                                visible: false,
                                                fixedcenter: true,
                                                width: "30em",
                                                zIndex: 250,
                                                buttons: [{text: this.msg("button.ok"), handler: checkinDocument, isDefault: true},
                                                    {text: this.msg("button.cancel"), handler: handleCancel}]
                                            });

                                            this.versionDialog.render(document.body);

                                            var elements = Dom.getElementsByClassName('yui-button', 'span');
                                            Dom.addClass(elements[0], 'alf-primary-button');
                                        }

                                        var majorVersion, minorVersion;

                                        var majorMinor = record.version.split(".");
                                        if (majorMinor.length == 2) {
                                            // Set the version label in the dialog
                                            minorVersion = majorMinor[0] + "." + (parseInt(majorMinor[1]) + 1),
                                                majorVersion = "" + (parseInt(majorMinor[0]) + 1) + ".0";
                                        }

                                        this.versionDialog.setBody('<div id="' + this.id +
                                        '-dialog" class="google-docs-version"><div class="hd">' + this.msg("label.header") +
                                        '</div><div class="bd"><form id="' + this.id +
                                        '-form" action="' + actionUrl +
                                        '" method="POST"><div id="' + this.id +
                                        '-versionSection-div"><div class="yui-gd"><div class="yui-u first"><span>' + this.msg("label.version") +
                                        '</span></div><div class="yui-u"><input id="' + this.id +
                                        '-minorVersion-radioButton" type="radio" name="majorVersion" value="false" checked="checked" tabindex="0"/><label for="' + this.id +
                                        '-minorVersion-radioButton" id="' + this.id +
                                        '-minorVersion">' + this.msg("label.minorVersion", minorVersion) +
                                        '</label></div></div><div class="yui-gd"><div class="yui-u first">&nbsp;</div><div class="yui-u"><input id="' + this.id +
                                        '-majorVersion-radioButton" type="radio" name="majorVersion" value="true" tabindex="0"/><label for="' + this.id +
                                        '-majorVersion-radioButton" id="' + this.id +
                                        '-majorVersion">' + this.msg("label.majorVersion", majorVersion) +
                                        '</label></div></div><div class="yui-gd"><div class="yui-u first"><label for="' + this.id +
                                        '-description-textarea">' + this.msg("label.comments") +
                                        '</label></div><div class="yui-u"><textarea id="' + this.id +
                                        '-description-textarea" name="description" cols="80" rows="4" tabindex="0"></textarea></div></div></div><div class="bdft"><input id="' + this.id +
                                        '-nodeRef" type="hidden" name="nodeRef" value="' + record.nodeRef + '" /><input id="' + this.id +
                                        '-override" type="hidden" name="override" value="false" /></div></form></div></div>');

                                        this.versionDialog.show();
                                    }
                                    else
                                    {
                                        Alfresco.GoogleDocs.showMessage({
                                            text: this.msg("googledocs.actions.checkingin"),
                                            displayTime: 0,
                                            showSpinner: true
                                        });

                                        Alfresco.GoogleDocs.request({
                                            url: actionUrl,
                                            method: "POST",
                                            dataObj: {
                                                nodeRef: record.nodeRef,
                                                override: saveDiscardConfirmed
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
        }
    }),


    /**
     * Resume editing a document that is being edited in Google Docs
     *
     * @method onGoogledocsActionResume
     * @param record {object} Object literal representing the file or folder on which the work should be performed
     */
        YAHOO.Bubbling.fire("registerAction", {
            actionName: "onGoogledocsActionResume",
            fn: function dlA_onGoogledocsActionResume(record) {

                Alfresco.GoogleDocs.showMessage({
                    text: this.msg("googledocs.actions.resume"),
                    displayTime: 0,
                    showSpinner: true
                });

                Alfresco.GoogleDocs.requestOAuthURL.call(this, {
                    onComplete: {
                        fn: function () {
                            Alfresco.GoogleDocs.checkGoogleLogin.call(this, {
                                onLoggedIn: {
                                    fn: function () {
                                        Alfresco.GoogleDocs.request.call(this, {
                                            url: Alfresco.constants.PROXY_URI + 'googledocs/uploadContent?nodeRef=' + record.nodeRef,
                                            method: "POST",
                                            requestContentType: Alfresco.util.Ajax.JSON,
                                            dataObj: {},
                                            successCallback: {
                                                fn: function (response) {
                                                    navigateToEditorPage(response.json.editorUrl);
                                                    location.reload();
                                                },
                                                scope: this
                                            },
                                            failureCallback: {
                                                fn: function () {
                                                    Alfresco.GoogleDocs.showMessage({
                                                        text: this.msg("googledocs.actions.editing.failure"),
                                                        displayTime: 2.5,
                                                        showSpinner: false
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
                        scope: this
                    }
                });
            }
        }),

        YAHOO.Bubbling.fire("registerAction", {
            actionName: "onGoogledocsActionCancel",
            fn: function dlA_onGoogledocsActionCancel(record) {

                Alfresco.GoogleDocs.showMessage({
                    text: this.msg("googledocs.actions.cancel"),
                    displayTime: 0,
                    showSpinner: true
                });
                var me = this,
                    displayName = record.displayName,
                    actionUrl = Alfresco.constants.PROXY_URI + "googledocs/discardContent";

                var success =
                {
                    fn: function (response) {
                        location.reload();
                    }
                };

                var failure =
                {
                    fn: function (response) {
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
                                                Alfresco.GoogleDocs.request({
                                                    url: actionUrl,
                                                    method: "POST",
                                                    dataObj: {
                                                        nodeRef: record.nodeRef,
                                                        override: true
                                                    },
                                                    successCallback: success,
                                                    failureCallback: failure
                                                });
                                                this.hide();
                                                location.reload();
                                            }
                                        },
                                        {
                                            text: me.msg("button.cancel"),
                                            handler: function cancelSave() {
                                                Alfresco.GoogleDocs.hideMessage();
                                                this.hide();
                                            },
                                            isDefault: true
                                        }]
                                });

                            var elements = Dom.getElementsByClassName('yui-button', 'span', 'prompt');
                            Dom.addClass(elements[0], 'alf-primary-button');
                        }
                        else {
                            Alfresco.GoogleDocs.showMessage({
                                text: me.msg("googledocs.actions.cancel.failure"),
                                displayTime: 2.5,
                                showSpinner: false
                            });

                            //GOOGLEDOCS-305 reload the page after error message is shown.
                            window.setTimeout(
                                function()
                                {
                                    location.reload();
                                },
                                2500);
                        }
                    }
                };

                Alfresco.util.PopupManager.displayPrompt(
                    {
                        title: this.msg("googledocs.cancelAction.title"),
                        text: this.msg("googledocs.cancelAction.text"),
                        noEscape: true,
                        buttons: [
                            {
                                text: me.msg("button.yes"),
                                handler: function submitDiscard() {
                                    this.hide();

                                    Alfresco.GoogleDocs.showMessage({
                                        text: me.msg("googledocs.actions.cancel"),
                                        displayTime: 0,
                                        showSpinner: true
                                    });

                                    Alfresco.GoogleDocs.requestOAuthURL.call(this, {
                                        onComplete: {
                                            fn: function () {
                                                Alfresco.GoogleDocs.checkGoogleLogin.call(this, {
                                                    onLoggedIn : {
                                                        fn: function () {
                                                            Alfresco.GoogleDocs.request.call(this, {
                                                                url: actionUrl,
                                                                method: "POST",
                                                                dataObj: {
                                                                    nodeRef: record.nodeRef,
                                                                    override: false
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

                                    //Begin
                                         //End
                                },
                                isDefault: true
                            },
                            {
                                text: me.msg("button.no"),
                                handler: function cancelSave() {
                                    Alfresco.GoogleDocs.hideMessage();
                                    this.hide();
                                },
                                isDefault: true
                            }]
                    });

                var elements = Dom.getElementsByClassName('yui-button', 'span', 'prompt');
                Dom.addClass(elements[0], 'alf-primary-button');
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
            actionName: "onGoogledocsActionCreateDocument",
            fn: function dlA_onGoogledocsActionCreateDocument(record) {
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
            actionName: "onGoogledocsActionCreateSpreadsheet",
            fn: function dlA_onGoogledocsActionCreateSpreadsheet(record) {
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
            actionName: "onGoogledocsActionCreatePresentation",
            fn: function dlA_onGoogledocsActionCreatePresentation(record) {
                createGoogleDoc.call(this, record, "presentation");
            }
        });

    /**
     * resumeSharing constructor.
     *
     * socialPublishing is considered a singleton so constructor should be treated as private,
     * please use Alfresco.module.getsocialPublishingInstance() instead.
     *
     * @param {string} htmlId The HTML id of the parent element
     * @return {Alfresco.GoogleDocs.resumeSharing} The new socialPublishing instance
     * @constructor
     * @private
     */
    Alfresco.GoogleDocs.resumeSharing = function (containerId) {
        this.name = "Alfresco.GoogleDocs.resumeSharing";
        this.id = containerId;

        var instance = Alfresco.util.ComponentManager.get(this.id);
        if (instance !== null) {
            throw new Error("An instance of Alfresco.GoogleDocs.resumeSharing already exists.");
        }

        /* Register this component */
        Alfresco.util.ComponentManager.register(this);

        // Load YUI Components
        Alfresco.util.YUILoaderHelper.require(["button", "container"], this.onComponentsLoaded, this);

        return this;

    };

    Alfresco.GoogleDocs.resumeSharing.prototype =
    {

        /**
         * The default config for the state for the resume dialog.
         * The caller can override these properties in the show() method.
         *
         * @property defaultConfig
         * @type object
         */
        defaultConfig: {
            nodeRef: null,
            currentUser: null,
            permissions: null,
            completeFn: null,
            skipFn: null,
            width: "40em"
        },

        /**
         * The merged result of the defaultConfig and the config passed in
         * to the show method.
         *
         * @property showConfig
         * @type object
         */
        config: {},

        /**
         * Object container for storing YUI widget and HTMLElement instances.
         *
         * @property widgets
         * @type object
         */
        widgets: {},

        /**
         * Fired by YUILoaderHelper when required component script files have
         * been loaded into the browser.
         *
         * @method onComponentsLoaded
         */
        onComponentsLoaded: function GDRS_onComponentsLoaded() {
            // Shortcut for dummy instance
            if (this.id === null) {
                return;
            }
        },

        /**
         * Show can be called multiple times and will display the dialog
         * in different ways depending on the config parameter.
         *
         * @method show
         * @param config {object} describes how the dialog should be displayed
         * The config object is in the form of:
         * {
       *    nodeRef: {string},  // the nodeRef
       *    currentUser: {string}   // Email address of the current user in Google
       *    permissions: {array}   // List of permissions to render into the dialog
       * }
         */
        show: function GDRS_show(config) {
            // Merge the supplied config with default config and check mandatory properties
            this.config = YAHOO.lang.merge(this.defaultConfig, config);
            if (this.config.nodeRef === undefined) {
                throw new Error("A nodeRef must be provided");
            }

            // If this.widgets.panel exists, but is for a different nodeRef, start again.
            if (this.widgets.panel) {
                this.widgets.panel.destroy;
            }

            // If it hasn't load the gui (template) from the server
            Alfresco.util.Ajax.request(
                {
                    url: Alfresco.constants.URL_SERVICECONTEXT + "modules/googledocs/resume-sharing?nodeRef=" + this.config.nodeRef + "&htmlid=" + this.id,
                    successCallback: {
                        fn: this.onTemplateLoaded,
                        scope: this
                    },
                    failureMessage: Alfresco.util.message("googledocs.error.resumeSharingTemplateError", this.name),
                    execScripts: true
                });

            // Register the ESC key to close the dialog
            this.widgets.escapeListener = new KeyListener(document,
                {
                    keys: KeyListener.KEY.ESCAPE
                },
                {
                    fn: this.onCancelDialog,
                    scope: this,
                    correctScope: true
                });

        },

        /**
         * Called when the dialog HTML template has been returned from the server.
         * Creates the YUI Panel instance
         *
         * @method onTemplateLoaded
         * @param response {object} a Alfresco.util.Ajax.request response object
         */
        onTemplateLoaded: function GDRS_onTemplateLoaded(response) {
            // Check that some mark-up was returned. If an empty response is provided then the list of permissions
            // was empty, so we can chains straight through to the continue function
            if (response.serverResponse.responseText.indexOf(this.id) === -1) {
                if (this.config.onComplete && this.config.onComplete.fn && typeof this.config.onComplete.fn == "function") {
                    this.config.onComplete.fn.call(this.config.onComplete.scope || window, {});
                }
                else {
                    throw new Error("No continue function was defined");
                }
                return;
            }

            // Hide the wait message - this will be re-displayed later if Cancel or Continue are pressed, but should not be there if the dialog is closed
            Alfresco.GoogleDocs.hideMessage();

            // Inject the template from the XHR request into a new DIV element
            var containerDiv = document.createElement("div");
            containerDiv.innerHTML = response.serverResponse.responseText;

            var dialogDiv = YAHOO.util.Dom.getFirstChild(containerDiv);

            // Create the panel from the HTML returned in the server reponse
            this.widgets.panel = Alfresco.util.createYUIPanel(dialogDiv, {
                width: this.config.width
            });

            // associate the panel with a nodeRef so we know when to refresh or redisplay it:
            this.widgets.panel.nodeRef = this.config.nodeRef;

            // Save a reference to HTMLElements
            this.widgets.headerText = Dom.get(this.id + "-header-span");
            this.widgets.cancelButton = Alfresco.util.createYUIButton(this, "cancel-button", this.onCancelButtonClick);
            this.widgets.continueButton = Alfresco.util.createYUIButton(this, "continue-button", this.onContinueButtonClick);
            this.widgets.formContainer = Dom.get(this.id + "-form");

            this.widgets.selectMenu = new YAHOO.widget.Button(this.id + "-select-button",
                {
                    type: "menu",
                    menu: this.id + "-select-menu",
                    lazyloadmenu: false
                });
            this.widgets.selectMenu.getMenu().subscribe("click", this.onSelectAllCheckboxToggle, this, true);

            // Row checkboxes
            this.widgets.checkboxes = [];
            Dom.getElementsByClassName("permission-checkbox", "input", this.widgets.formContainer, function (el) {
                Event.addListener(el, "click", this.onPermissionCheckboxToggle, this, true);
                this.widgets.checkboxes.push(el);
            }, this, true);

            // Row drop-downs
            this.widgets.roleButtons = [];
            Dom.getElementsByClassName("role-button", "button", this.widgets.formContainer, function (el) {
                var isChecked = Dom.getAttribute(el.id.replace("-role-button", "-checkbox"), "checked"),
                    button = new YAHOO.widget.Button(el,
                        {
                            type: "menu",
                            menu: el.id.replace("-role-button", "-role-select"),
                            lazyloadmenu: false,
                            disabled: !(isChecked === true || isChecked === "checked")
                        });
                var menu = button.getMenu(), me = this;
                if (menu) {
                    menu.subscribe("click", function (p_sType, p_aArgs) {
                        var menuItem = p_aArgs[1]; // YAHOO.widget.MenuItem instance
                        if (menuItem) {
                            button.set("label", menuItem.cfg.getProperty("text"));
                            button.set("value", menuItem.value);
                        }
                    });
                }
                this.widgets.roleButtons.push(button);
            }, this, true);

            this.widgets.sendEmail = Dom.get(this.id + "-checkbox-sendEmail");

            // Show panel
            this._showPanel();
        },

        /**
         * Fired when the user clicks the cancel button.
         * Closes the panel.
         *
         * @method onCancelButtonClick
         */
        onCancelButtonClick: function GDRS_onCancelButtonClick() {
            this.closeDialogue();
        },

        /**
         * Fired when the user clicks the close button.
         * Closes the panel.
         *
         * @method onCancelDialog
         */
        onCancelDialog: function GDRS_onCancelDialog() {
            this.closeDialogue();
        },

        /**
         * Fired when the user clicks the "Continue" button on the dialogue
         *
         * @method onContinueButtonClick
         */
        onContinueButtonClick: function GDRS_onContinueButtonClick() {
            // Create the list of permissions from the UI
            var permissions = this._getPermissionsList(),
                sendEmail = this.widgets.sendEmail !== null ? Dom.getAttribute(this.widgets.sendEmail, "checked") : true; // Assume email should be sent if checkbox not present

            this.closeDialogue();
            if (this.config.onComplete && this.config.onComplete.fn && typeof this.config.onComplete.fn == "function") {
                this.config.onComplete.fn.call(this.config.onComplete.scope || window, {
                    permissions: {
                        items: permissions,
                        sendEmail: sendEmail
                    }
                });
            }
            else {
                throw new Error("No continue function was defined");
            }
        },

        /**
         *
         * Closes the fialogue and tidys up any loose ends
         *
         * @method closeDialogue
         */
        closeDialogue: function GDRS_closeDialogue() {
            // Hide the panel
            this.widgets.panel.hide();

            // Disable the Esc key listener
            this.widgets.escapeListener.disable();

        },

        /**
         * Build up a list of currently-selected permissions from the UI
         *
         * @returns Array of permission objects, each containing properties 'authorityId', '' and 'roleName'
         * @private
         */
        _getPermissionsList: function GDRS__getPermissionsList() {
            var list = [];
            for (var i = 0; i < this.widgets.checkboxes.length; i++) {
                if (Dom.getAttribute(this.widgets.checkboxes[i], "checked")) {
                    var pair = Dom.getAttribute(this.widgets.checkboxes[i], "value").split("|"),
                        authorityType = pair[0],
                        authorityId = pair[1],
                        roleName = this.widgets.roleButtons[i].get("value");
                    if (roleName) {
                        list.push({
                            authorityId: authorityId,
                            authorityType: authorityType,
                            roleName: roleName
                        });
                    }
                    else {
                        throw new Error("Role name cannot be null or empty!");
                    }
                }
            }
            return list;
        },

        /**
         *
         * Triggered when a line item check box is selected or unselected.
         *
         * @method onPermissionCheckboxToggle
         */
        onPermissionCheckboxToggle: function GDRS_onPermissionCheckboxToggle(e, matchEl, obj) {
            var parts = e.currentTarget.id.split("-checkbox-");
            if (parts.length == 2) {
                var index = parseInt(parts[1], 10),
                    isChecked = Dom.getAttribute(e.currentTarget, "checked");
                this.widgets.roleButtons[index].set("disabled", !isChecked);
            }
            else {
                throw new Error("Bad checkbox ID, must contain '-checkbox-");
            }
        },

        /**
         * Triggered when the "Select" dropdown is used to select all or select none
         *
         * @method onSelectAllCheckboxToggle
         */
        onSelectAllCheckboxToggle: function GDRS_onSelectAllCheckboxToggle(p_sType, p_aArgs) {
            var menuItem = p_aArgs[1]; // YAHOO.widget.MenuItem instance
            if (menuItem) {
                var isChecked = menuItem.value == "all";
                for (var i = 0; i < this.widgets.checkboxes.length; i++) {
                    Dom.setAttribute(this.widgets.checkboxes[i], "checked", isChecked ? "checked" : null);
                    // This *should* chain through to the event handlers on the row checkboxes, but it does not in Chrome. So do it here as well.
                    this.widgets.roleButtons[i].set("disabled", !isChecked);
                }
            }
            else {
                throw new Error("No MenuItem instance passed");
            }
        },

        /**
         * Adjust the gui according to the config passed into the show method.
         *
         * @method _applyConfig
         * @private
         */
        _applyConfig: function GDRS__applyConfig() {
        },

        /**
         * Prepares the gui and shows the panel.
         *
         * @method _showPanel
         * @private
         */
        _showPanel: function GDRS__showPanel() {
            // Apply the config before it is shown
            this._applyConfig();

            // Enable the Esc key listener
            this.widgets.escapeListener.enable();

            // Show the panel
            this.widgets.panel.show();
        }
    };

    Alfresco.GoogleDocs.getResumeSharingInstance = function () {
        var instanceId = "googledocs-resumeSharing-instance";
        return Alfresco.util.ComponentManager.get(instanceId) || new Alfresco.GoogleDocs.resumeSharing(instanceId);
    };

})();
