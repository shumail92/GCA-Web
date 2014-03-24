require(["lib/models", "lib/tools"], function(models, tools) {
    "use strict";


    /**
     * admin/abstracts view model.
     *
     *
     * @param confId
     * @returns {OwnersListViewModel}
     * @constructor
     */
    function adminAbstractsViewModel(confId) {

        if (! (this instanceof adminAbstractsViewModel)) {
            return new adminAbstractsViewModel(confId);
        }

        var self = this;
        self.message = ko.observable(null);
        self.abstractsData = null;
        self.conference = ko.observable(null);
        self.abstracts = ko.observableArray(null);

        self.init = function() {
            self.ensureData();
            ko.applyBindings(window.dashboard);
        };

        self.isLoading = function(status) {
            if (status === false) {
                self.message(null);
            } else {
                self.message({message: "Loading data!", level: "alert-info", desc: "Please wait..."});
            }
        };

        self.setError = function(level, text, description) {
            if (text === null) {
                self.message(null);
            } else {
                self.message({message: text, level: 'alert-' + level, desc: description});
            }
        };


        //helper functions
        self.makeAbstractLink = function(abstract, conference) {
            return "/myabstracts/" + abstract.uuid + "/edit";
        };

        self.mkAuthorList = function(abstract) {

            if(abstract.authors.length < 1) {
                return "";
            }

            var text = abstract.authors[0].lastName;

            if (abstract.authors.length > 1) {
                text += " et. al.";
            }

            return text;
        };

        //Data IO
        self.ioFailHandler = function(jqxhr, textStatus, error) {
            var err = textStatus + ", " + error;
            console.log( "Request Failed: " + err );
            self.setError("danger", "Error while fetching data from server", error);
        };

        self.ensureData = function() {
            console.log("ensureDataAndThen::");
            self.isLoading(true);
            if (self.abstractsData !== null) {
                self.isLoading(false);
                return;
            }

            //now load the data from the server
            var confURL ="/api/conferences/" + confId;
            $.getJSON(confURL, onConferenceData).fail(self.ioFailHandler);

            //conference data
            function onConferenceData(confObj) {
                var conf = models.Conference.fromObject(confObj);
                self.conference(conf);
                //now load the abstract data
                $.getJSON(confURL + "/allAbstracts", onAbstractData).fail(self.ioFailHandler);
            }

            //abstract data
            function onAbstractData(absArray) {
                var absList = models.Abstract.fromArray(absArray);
                self.abstractsData = absList;
                self.abstracts(absList);

                self.isLoading(false);
            }
        };

    }

    $(document).ready(function() {

        var data = tools.hiddenData();

        console.log(data.conferenceUuid);

        window.dashboard = adminAbstractsViewModel(data.conferenceUuid);
        window.dashboard.init();
    });


});