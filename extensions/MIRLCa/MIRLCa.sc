//
// This class inherits from MIRLCRep2
// You will need the following JSON files (generated using the FluCoMa library) to be stored  in the same path than MIRLCRep2:
// - model.JSON
// - standardizer.JSON
// - pca.JSON
//
// Refer to MIRLCRep2 for the following setup changes:
// @new users, control the following customizable vars:
// - Freesound.token = "<your_api_key>"
// - path: replace current directory to your own directory to store downloaded sounds and record the text file with the credits, or change it to "Platform.defaultTempDir" or "/tmp/"
// - debugging: True/False


MIRLCa : MIRLCRep2 {

	var test_dataset;
	var stand_test_dataset;
	var standardizer;
	var pca;
	var classifier;
	var test_predicted_label_dataset;
	var is_training;
	var temp_list_training;
	var sndid_t;
	var sndid_old_t;
	var manual_dataset_dict;
	var training_dict;
	var training_label_dict;
	var test_dict;
	var test_label_dict;
	var training_dataset;
	var training_label_dataset;
	var test_dataset_fixed;
	var test_label_dataset_fixed;
	var stand_dataset;
	var stand_test_dataset_fixed;
	var mode_training;
	var modelfilepath;

	*new { | backend = 0, dbSize = 478456, path = (Platform.defaultTempDir), creditsPath = (Platform.defaultTempDir), modelsPath = (Platform.defaultTempDir) |
		^super.new(backend, dbSize, path, creditsPath).initagent(modelsPath);
    }

	initagent { |modelsPath|
		var randomnum = 100000.rand;
		var test_dataset_name = \mlpclassify_testdata++randomnum.asSymbol;
		var stand_test_dataset_name = \mlpclassify_stand_test_data++randomnum.asSymbol;
		var test_predicted_label_dataset_name = \mlpclassify_predictlabels++randomnum.asSymbol;
		var mlpclassify_trainingdata = \mlpclassify_trainingdata++randomnum.asSymbol;
		var mlpclassify_labels = \mlpclassify_labels++randomnum.asSymbol;
		var mlpclassify_testdata = \mlpclassify_testdata++randomnum.asSymbol;
		var mlpclassify_testlabels = \mlpclassify_testlabels++randomnum.asSymbol;
		var mlpclassify_standdata = \mlpclassify_standdata++randomnum.asSymbol;
		var mlpclassify_stand_test_data = \mlpclassify_stand_test_data++randomnum.asSymbol;

		modelfilepath = modelsPath;
		is_training = False;
		manual_dataset_dict = Dictionary.new;
		training_dict = Dictionary();
		training_label_dict = Dictionary();
		test_dict = Dictionary();
		test_label_dict = Dictionary();

		postln("INFO MIRLCa: Machine learning models are loaded from (performance mode) and will be saved at (training mode): " ++ modelfilepath);
		postln("INFO MIRLCa: Sounds will be downloaded at: " ++ directoryPath);
		postln("INFO MIRLCa: A sound credits list will be created at: " ++ creditsfilepath);

		server.waitForBoot {

		"I'm an agent".postln;

		fork {
		training_dataset.free;
		training_dataset = FluidDataSet(server, mlpclassify_trainingdata); server.sync;
		training_label_dataset.free;
		training_label_dataset = FluidLabelSet(server, mlpclassify_labels); server.sync;
		test_dataset_fixed.free;
		test_dataset_fixed = FluidDataSet(server, mlpclassify_testdata); server.sync;
		test_label_dataset_fixed.free;
		test_label_dataset_fixed = FluidLabelSet(server, mlpclassify_testlabels); server.sync;
		test_dataset.free;
		test_dataset = FluidDataSet(server, test_dataset_name); server.sync;
		stand_dataset.free;
		stand_dataset = FluidDataSet(server, mlpclassify_standdata); server.sync;
		stand_test_dataset_fixed.free;
		stand_test_dataset_fixed = FluidDataSet(server, mlpclassify_stand_test_data);
		stand_test_dataset.free;
		stand_test_dataset = FluidDataSet(server, stand_test_dataset_name); server.sync;
		test_predicted_label_dataset.free;
		test_predicted_label_dataset = FluidLabelSet(server, test_predicted_label_dataset_name); server.sync;
		standardizer.free;
		standardizer = FluidStandardize(server); server.sync;
		pca.free;
		pca = FluidPCA(server,20); server.sync;
		classifier.free;
		classifier = FluidMLPClassifier(server, [14], FluidMLPClassifier.relu, 10000, 0.01, 0.9, 10, 0.2);
		server.sync;

		//Checking if the model files exist: standardizer.JSON, pca.JSON and model.JSON
		if ( File.exists( modelfilepath ++ "standardizer.JSON" ),
			{ standardizer.read(modelfilepath ++ "standardizer.JSON"); },
			{ postln("ERROR: standardizer.JSON file was not found in the directory "++ modelfilepath) }
		);

		if ( File.exists(modelfilepath ++ "pca.JSON" ),
			{ pca.read(modelfilepath ++ "pca.JSON"); },
			{ postln("ERROR: pca.JSON file was not found in the directory "++ modelfilepath) }
		);

		if ( File.exists(modelfilepath ++ "model.JSON" ),
			{ classifier.read(modelfilepath ++ "model.JSON"); },
			{ postln("ERROR: model.JSON file was not found in the directory "++ modelfilepath) }
		);

		}; // End fork

		}; // End server.waitForBoot

	}

    //------------------//
    // GET SOUND BY ID
    //------------------//
    // Performance mode: This public function is used by the live coder to get a sound by ID
    // params: id, size
	id { | id = 31362, size = 1 |

		 backendClass.getSound ( id,
            { | f |

				var tmpSnd;
				tmpSnd = f;

			if ( tmpSnd.isNil, {
					postln("ERROR: There was a problem downloading the sound with ID " ++ id ++ "\nINFO: Please try again.");},
				{
					if ( tmpSnd["similar_sounds"].isNil , {
						postln("ERROR: This sound does not have similar_sounds information required by the MIRLCa agent.\nWARNING: Please try another sound.");});

					if ( tmpSnd["detail"] == "Not found.", {
						postln("ERROR: Sound details not found.\nWARNING: Please try another sound.");
					});

					if ( tmpSnd["detail"] != "Not found." && tmpSnd["similar_sounds"].notNil , { // ID exists
						this.storeplaysound(tmpSnd, size);

					});
				}); // End If tmpSnd.isNil

			});
    } //-//

    //------------------//
    // STORE AND PLAY SOUND
    //------------------//
    // Performance & Training mode: Private function that manages to play and store a sound by ID.

	storeplaysound { | currentsnd, currentsize |

		index = metadata.size;
		// postln("index: "+index);
		// postln("currentsnd: "+currentsnd);
		// postln("currentsize: "+currentsize);

		try {

		metadata.add(index -> currentsnd);
		// postln("metadata: "+metadata[index]);

		if ( currentsize == 1,
		{ this.loadmetadata(currentsize); },
		{ // currentsize > 1
			if ( (metadata.size - poolsizeold) == currentsize,
			{ // need to wait until asynchronous call is ready! once all sounds are added in the dictionary, they can be retrieved
			this.loadmetadata(currentsize);
			});
		});
		} // end try
		{ |error| [\catchgetSound, error].postln }; // end catch error
		try {
			file.open(creditsfilename,"a");
			file.write(currentsnd["name"] ++ " by " ++ currentsnd["username"] ++ " (" ++ currentsnd["url"] ++") licensed under " ++ currentsnd["license"] + "\n");
			file.close();
		} //end try
		{ |error| [\catchFileWrite, error].postln }; // end catch error

	} //-//

    //------------------//
    // GET SOUND BY ID
    //------------------//
    // Performance & Training mode: Private function used by random, tag, and similar to get sounds
	// It has two modes: the performance mode and the training mode.
    // params: id, size
	// Overwriting this function to avoid sounds without analysis information
    prid { | id = 31362, size = 1 |

		// try {
        backendClass.getSound( id,
            { | f |
                //available metadata: "id","url","name","tags","description","geotag","created","license","type","channels","filesize""bitrate","bitdepth","duration","samplerate","username","Jovica","pack","pack_name","download","bookmark","previews","preview-lq-mp3","preview-hq-ogg","preview-hq-mp3","images","num_downloads","avg_rating","num_ratings","rate":,"comments","num_comments","comment","similar_sounds","analysis","analysis_frames","analysis_stats"

				var tmpSnd;
				tmpSnd = f;

				if ( is_training == False, // checks have been made of similarity data and file existence data (analysis data is not needed here)
					{
						this.storeplaysound(tmpSnd, size);
					},
					{ // IF Training is TRUE, only 1 sound at a time
						if ( is_training == True, { // checks have been made of analysis data
							index = 0;
							metadata.add(index -> tmpSnd);
							this.loadmetadata_t(1); // size = 1, only 1 sound at a time
							// TODO: Write the filenames during training distinguishing	between good and bad sounds
						});
					}); // End IF is_training == False

				// ::Deprecated: These CHECKS are done before calling this function
				/*if ( tmpSnd.isNil, {
					"ERROR: There was a problem downloading this sound.\nINFO: Please try again.".postln;
				}, {
					if ( snd["analysis"].isNil ) {
						"ERROR: Sound analysis does not exist.\nThis sound will be skipped from the list of candidates.".postln;
					};
					if ( tmpSnd["similar_sounds"].isNil , {
						postln ("This sound does not have similar_sounds information.\nPlease try again.");
					});

					if ( tmpSnd["detail"]=="Not found." , {
						"ERROR: Sound details not found.\nPlease try again.".postln;
					});

					if ( tmpSnd["detail"] != "Not found." && tmpSnd["similar_sounds"].notNil, {

					});

				}); // End IF tmpSnd.isNil*/
				// ::End Deprecated

        } ); // end backendClass.getSound
    } //--//

    //------------------//
    // SIMILAR SOUNDS
    //------------------//
	// Performance mode: Public function that returns the best candidate of a list of similar sounds given a target sound. It can only return one sound for now.
	similar { | targetnumsnd = 0, size = 1, cand = 15 |

		var index_offset = 1; //*
		var candidates = cand; // 1 page has 15 sounds. %TODO: load a second page asynchronously using FSPager as an asynchronous action using the method next
		var query_params = Dictionary[
			"fields"->"id,name,analysis",
			"descriptors"->"lowlevel.mfcc"
		];

		target = metadata[targetnumsnd];  // before: metadata[targetnumsnd - 1];

		// temp solutions to avoid unhandled limits
		if (targetnumsnd >= metadata.size, { postln("INFO: This target sound does not exist.\nPlease choose another target sound."); }); // temp solution: limit retrieval to 1 sound
		if (size != 1, {size = 1}); // temp solution: limit retrieval to 1 sound
		if (candidates > 15, {candidates = 15}); // temp solution: limit to 14 sounds limited by the FSPager

		try {
		target.getSimilar(params:query_params,
			action: { |p|

		/*// Getting info of the incoming sound
		p[i].id.postln;
		p[i].dict.keys.postln;
		p[i].dict.values.postln;*/
			if ( p.isNil,
			{
				postln("ERROR: There was a problem searching for a list of candidates of similar sounds from this target sound.\nINFO: Please try again.");
			}, {
				this.getbestfromtarget(p, candidates, index_offset);
			});



		});
		}//end try
		{|error| [\catchSimilarMethod, error].postln; postln("ERROR: getSimilar() was not understood."); };

	} //--//

    //------------------//
    // QUERY BY TAG
    //------------------//
    // Performance mode: Public function that returns the best candidate of a sound by one defined tag. It can only return one sound for now.

    tag { | tag = "noise", size = 1, cand = 15 |

		var index_offset = 0; //*
		var candidates = cand; // 1 page has 15 sounds. %TODO: load a second page asynchronously using FSPager as an asynchronous action using the method next
		var query_params = Dictionary[
			"fields"->"id,name,analysis",
			"descriptors"->"lowlevel.mfcc"
		];

		if (size != 1, {size = 1}); // temp solution: limit retrieval to 1 sound
		if (candidates > 15, {candidates = 15}); // temp solution: limit to 14 sounds provided by FSPager

		// try { // seems to be giving an error
        backendClass.textSearch( query: tag, params: query_params,

            action: { |p|
				if ( p.isNil,
				{
					postln("ERROR: There was a problem searching for a list of candidates from this tag.\nINFO: Please try again.");
				}, {
					this.getbestfromtarget( p, candidates, index_offset );
				});

		    });
		// } //end try
		// { |error| [\catchTagMethod, error].postln };

    } //--//

    //------------------//
    // QUERY BY RANDOM
    //------------------//
    // Performance mode: Public function that returns the best candidate of a list of random sounds. It can only return one sound for now.

    random { | size = 1, cand = 7 |

		var sndid;

        if ( debugging == True, { postln("INFO: Sounds selected by random: " ++ size);} );
		postln("INFO: Looking for a 'good' random sound...");
		postln("INFO: The number of candidates for the best random sound is: " ++ cand);
	    postln("INFO: The larger the number, the more time it will take. Be patient.");

		sndid = this.getbest(cand);
		// sndid = rrand (1, databaseSize);

    } //--//

    //------------------//
    // RETURNS BEST CANDIDATE
    //------------------//
	// Performance mode: Private function used to return the best candidate to the random() method.

	getbest { | candidates |

		var sndid;
		var temp_list_ids = List.new;
		var result;
		var temp_list = List.new;
		var temp_dict = Dictionary.new;
		var test_dataset_content = Dictionary.new;
		var temp_counter = 0;
		var cond1 = Condition.new;
		var cond2 = Condition.new;
		var cond3 = Condition.new;

		// 1) generate a list of random numbers => sound IDs
		candidates.do({ |i|
			temp_list_ids.add(rrand (1, databaseSize));
			//temp_list_ids.postln;
		});

		postln("INFO: The candidates for the best random sound are: \n" ++ temp_list_ids);
		// 2) get the analysis

		fork {
		candidates.do { | index |

			5.wait; // This is the amount of time in sec. For example, 10 sec means 6 sounds per minute.
			cond1.test = false;
			cond2.test = false;

			sndid = temp_list_ids[index];
			// sndid.postln;
			// sndid = 3333; // for debugging purposes - 1 sound
			 if ( debugging == True, {
				// snd.dict.keys.postln;
				// snd.dict.values.postln;
				postln("INFO: Current analysed sound from the list of FS similar sounds:" ++ snd.dict["id"]);
			});

			backendClass.getSound ( sndid,
			{ |f|
			var tmpSnd;
			// ("getSound"+sndid+" "+f.id.asInteger).postln;
			temp_list = List.new;
			cond1.wait;
			tmpSnd = f;

			if ( tmpSnd.isNil, {
							postln("ERROR: There was a problem downloading the sound with ID" ++ temp_list_ids[index] ++ ".\nThis sound will be skipped from the list of candidates.");}, {
				// "INFO: Sound has been downloaded.".postln;
				// Extra comment:
				// Nicer and more compact code, but unfortunately it cannot be used because we need to keep the same order than in the training process.
				/*13.do{| i |
					temp_list.add(snd.analysis.lowlevel.mfcc.mean[i]);
					temp_list.add(snd["analysis"]["lowlevel"]["mfcc"]["var"][i]);
				};*/
				if (tmpSnd["analysis"].isNil) {
					postln("ERROR: Sound analysis does not exist.\nThis sound will be skipped from the list of candidates.");
				};

					if (tmpSnd["detail"]=="Not found.", { // TODO: For some reason this does not seem to work: If the sound does not exist it retrieves the message "ERROR: Message 'id' not understood."
						postln("ERROR: Sound details not found.\nSound not included.");
			});

					if ( tmpSnd["detail"] != "Not found." && tmpSnd["analysis"].notNil, {

						tmpSnd.getAnalysis("lowlevel.mfcc", { |val|

							temp_list.add(val.lowlevel.mfcc.mean[0]);
							temp_list.add(val.lowlevel.mfcc.mean[1]);
							temp_list.add(val.lowlevel.mfcc.mean[2]);
							temp_list.add(val.lowlevel.mfcc.mean[3]);
							temp_list.add(val.lowlevel.mfcc.mean[4]);
							temp_list.add(val.lowlevel.mfcc.mean[5]);
							temp_list.add(val.lowlevel.mfcc.mean[6]);
							temp_list.add(val.lowlevel.mfcc.mean[7]);
							temp_list.add(val.lowlevel.mfcc.mean[8]);
							temp_list.add(val.lowlevel.mfcc.mean[9]);
							temp_list.add(val.lowlevel.mfcc.mean[10]);
							temp_list.add(val.lowlevel.mfcc.mean[11]);
							temp_list.add(val.lowlevel.mfcc.mean[12]);
							temp_list.add(val["lowlevel"]["mfcc"]["var"][0]);
							temp_list.add(val["lowlevel"]["mfcc"]["var"][1]);
							temp_list.add(val["lowlevel"]["mfcc"]["var"][2]);
							temp_list.add(val["lowlevel"]["mfcc"]["var"][3]);
							temp_list.add(val["lowlevel"]["mfcc"]["var"][4]);
							temp_list.add(val["lowlevel"]["mfcc"]["var"][5]);
							temp_list.add(val["lowlevel"]["mfcc"]["var"][6]);
							temp_list.add(val["lowlevel"]["mfcc"]["var"][7]);
							temp_list.add(val["lowlevel"]["mfcc"]["var"][8]);
							temp_list.add(val["lowlevel"]["mfcc"]["var"][9]);
							temp_list.add(val["lowlevel"]["mfcc"]["var"][10]);
							temp_list.add(val["lowlevel"]["mfcc"]["var"][11]);
							temp_list.add(val["lowlevel"]["mfcc"]["var"][12]);

							if ( debugging == True, {
								postln("INFO: Storing MFCC information in a temp list, for each sound.");
								("temp_list:  " ++ temp_list).postln;
								// temp_list.size.postln;
								// temp_dict.postln;
								// temp_dict.size.postln;
							});

							// ("adding"+tmpSnd.id.asInteger).postln;
							temp_dict.add(tmpSnd.id.asInteger -> temp_list.asFloat); // dictionary key = ID; dictionary value = array
							// postln("temp_dict" ++ temp_dict);
							cond2.wait;
							// "INFO: Sound descriptors are downloaded.".postln;

							if ( debugging == True, {
								temp_dict.postln;
								temp_dict.keys.postln;
								temp_dict.size.postln;
							});

						}, true); // end snd.getanalysis

						}); // End if get analysis
					}); // End IF snd.isNil

			temp_counter = temp_counter + 1;

		}); // End of backendClass.getSound
			cond1.test = true;
			cond1.signal;
			cond2.test = true;
			cond2.signal;

		}; // End of size.do loop

		if ( temp_dict.size == 0, { // temp_dict.size == 0
				postln("********************************************");
				postln("ERROR: Something went wrong and there are no candidates.\nINFO: Please try again.");
				postln("********************************************");
			}, {
				// "Done".postln;
				postln("INFO: Selecting the best candidate...");
				// 3) evaluate which one is the best ID

				test_dataset_content = Dictionary.new;
				test_dataset_content.add(\cols -> 26); // number of inputs
				test_dataset_content.add(\data -> temp_dict);

				if ( debugging == True, {
					postln("Test_dataset_content: " ++ test_dataset_content);
					test_dataset_content.[\data].postln;
					test_dataset_content.size.postln;
				});

				// server.sync;
				test_dataset.load(test_dataset_content); server.sync;
				test_dataset.dump; server.sync;
				// test_dataset.write(modelfilepath ++ "mirlca_test_dataset.txt");server.sync;
				standardizer.transform(test_dataset, stand_test_dataset); server.sync;
				// stand_test_dataset.write(modelfilepath ++ "mirlca_standardizer.txt"); server.sync;
				stand_test_dataset.dump; server.sync;
				pca.transform(stand_test_dataset, stand_test_dataset, {
					// "Done".postln;
				}); server.sync;
				stand_test_dataset.dump; server.sync;
				// stand_test_dataset.write(modelfilepath ++ "mirlca_pca.txt"); server.sync;
				classifier.predict(stand_test_dataset, test_predicted_label_dataset, action:{"Test complete".postln}); server.sync;
				// test_predicted_label_dataset.dump;
				test_predicted_label_dataset.dump(action:{ |dict|
					var found = False;
					dict["data"].keysValuesDo{ |k,v|
						// 4) return the sound
						if ( v[0] =="good" && found == False, {
							// k.postln; // ID of the selected candidate
							this.prid(k, 1);
							"MIRLCa: Do you like this sound?".postln;
							found = True;
						});

					};
					if (found == False, {("WARNING: Only bad sounds were found from "++(candidates)++" candidates.\nWARNING:Try another sound.").postln});
				});
				// test_predicted_label_dataset.write(modelfilepath ++ "test_predicted_label_dataset.txt");
				server.sync;

			}); // End IF temp_dict.isNil

		}; // End of Fork

	} //--//


    //------------------//
    // RETURNS BEST CANDIDATE
    //------------------//
	// Performance mode: Private function used to return the best candidate to the tag() and similar() methods.

	getbestfromtarget { | p, candidates, index_offset = 0 |

		var temp_list = List.new; //*
		var temp_dict = Dictionary.new; //*
		var test_dataset_content = Dictionary.new; //*
		var temp_counter = 0; //*

		candidates.do { | index |

			snd = p[index_offset];

			// check if snd.id already exists, if so, take next sound
			// this needs to be revised to keep a window frame of n (size) sounds
			if ( metadata.size > 0,
				{
					temp_counter = 0;
					while ( { this.sndidexist(snd.id) == 1 },
						{
							temp_counter = temp_counter + 1;
							index_offset = index_offset + temp_counter;
							snd = p[index_offset];
							postln ("INFO: Repeated sound, getting another sound...");
					});
			});

			 if ( debugging == True, {
				// snd.dict.keys.postln;
				// snd.dict.values.postln;
				postln("INFO: Current analysed sound from the list of FS similar sounds" ++ snd.dict["id"]);
			});

			temp_list = List.new;

			// Extra comment:
			// Nicer and more compact code, but unfortunately it cannot be used because we need to keep the same order than in the training process.
			/*13.do{| i |
						temp_list.add(snd.analysis.lowlevel.mfcc.mean[i]);
				temp_list.add(snd["analysis"]["lowlevel"]["mfcc"]["var"][i]);
			};*/

			//TODO: add check data of sound "analysis" and sound "detail"

			if ( snd.isNil,
			{
				postln("ERROR: There was a problem downloading this sound.\nThis sound will be skipped from the list of candidates.");
				//this.messagesperforming;
			},{
				if ( snd["analysis"].isNil ) {
					postln("ERROR: Sound analysis does not exist.\nThis sound will be skipped from the list of candidates.");
				};

				if ( snd["detail"]=="Not found." , {
					postln("ERROR: Sound details not found.\nThis sound will be skipped from the list of candidates.");
				});

				if ( snd["detail"] != "Not found." && snd["analysis"].notNil, {

					temp_list.add(snd.analysis.lowlevel.mfcc.mean[0]);
					temp_list.add(snd.analysis.lowlevel.mfcc.mean[1]);
					temp_list.add(snd.analysis.lowlevel.mfcc.mean[2]);
					temp_list.add(snd.analysis.lowlevel.mfcc.mean[3]);
					temp_list.add(snd.analysis.lowlevel.mfcc.mean[4]);
					temp_list.add(snd.analysis.lowlevel.mfcc.mean[5]);
					temp_list.add(snd.analysis.lowlevel.mfcc.mean[6]);
					temp_list.add(snd.analysis.lowlevel.mfcc.mean[7]);
					temp_list.add(snd.analysis.lowlevel.mfcc.mean[8]);
					temp_list.add(snd.analysis.lowlevel.mfcc.mean[9]);
					temp_list.add(snd.analysis.lowlevel.mfcc.mean[10]);
					temp_list.add(snd.analysis.lowlevel.mfcc.mean[11]);
					temp_list.add(snd.analysis.lowlevel.mfcc.mean[12]);
					temp_list.add(snd["analysis"]["lowlevel"]["mfcc"]["var"][0]);
					temp_list.add(snd["analysis"]["lowlevel"]["mfcc"]["var"][1]);
					temp_list.add(snd["analysis"]["lowlevel"]["mfcc"]["var"][2]);
					temp_list.add(snd["analysis"]["lowlevel"]["mfcc"]["var"][3]);
					temp_list.add(snd["analysis"]["lowlevel"]["mfcc"]["var"][4]);
					temp_list.add(snd["analysis"]["lowlevel"]["mfcc"]["var"][5]);
					temp_list.add(snd["analysis"]["lowlevel"]["mfcc"]["var"][6]);
					temp_list.add(snd["analysis"]["lowlevel"]["mfcc"]["var"][7]);
					temp_list.add(snd["analysis"]["lowlevel"]["mfcc"]["var"][8]);
					temp_list.add(snd["analysis"]["lowlevel"]["mfcc"]["var"][9]);
					temp_list.add(snd["analysis"]["lowlevel"]["mfcc"]["var"][10]);
					temp_list.add(snd["analysis"]["lowlevel"]["mfcc"]["var"][11]);
					temp_list.add(snd["analysis"]["lowlevel"]["mfcc"]["var"][12]);

					if ( debugging == True, {
						postln("INFO: Storing MFCC information in a temp list, for each sound.");
						postln("temp_list:  " ++ temp_list);
						// temp_list.size.postln;
					});

					temp_dict.add(snd.id.asInteger -> temp_list.asFloat); // dictionary key = ID; dictionary value = array

					// if ( debugging == True, {
					// temp_dict.postln;
					// temp_dict.size.postln;
					// };

					// Original code from MIRLC, the sound is retrieved here
					// this.prid(snd.id, 1); // so that each sound is loaded directly played

				}); // End if sound analysis

			}); // End IF snd.isNil

		}; // End of size.do loop

		test_dataset_content = Dictionary.new;
		test_dataset_content.add(\cols -> 26); // number of inputs
		test_dataset_content.add(\data -> temp_dict);
		// "test_dataset_content".postln;
		// test_dataset_content.postln;

		fork {
			// server.sync;
			test_dataset.load(test_dataset_content); server.sync;
			test_dataset.dump; server.sync;
			// test_dataset.write(directoryPath ++ "mirlca_test_dataset.txt");server.sync;
			standardizer.transform(test_dataset, stand_test_dataset); server.sync;
			// stand_test_dataset.write(directoryPath ++ "mirlca_standardizer.txt");server.sync;
			stand_test_dataset.dump; server.sync;
			pca.transform(stand_test_dataset, stand_test_dataset, {
				"Done".postln;
			}); server.sync;
			stand_test_dataset.dump; server.sync;
			// stand_test_dataset.write(directoryPath ++ "mirlca_pca.txt");server.sync;
			classifier.predict(stand_test_dataset, test_predicted_label_dataset, action:{"Test complete".postln}); server.sync;
			// test_predicted_label_dataset.dump;
			test_predicted_label_dataset.dump(action:{ |dict|
				var found = False;
				dict["data"].keysValuesDo{ |k,v|

				// size.do { // TODO: implementthe retrieval of +1 sound
				if ( v[0] =="good" && found == False, {
					// k.postln;
					this.prid(k, 1); // so that each sound is loaded directly played
					"MIRLCa: Do you like this sound?".postln;
					found = True;
				});

				// }; // End of size.do loop

					//k.postln;
					//v.postln;
					//v.class.postln;
				};
				if (found == False, {postln("WARNING: Only bad sounds were found from "++(candidates)++" candidates.\nWARNING: Try another sound.")});
				}); // TODO: for similarity the total sounds returned are candidates-1
				test_predicted_label_dataset.write(directoryPath ++ "test_predicted_label_dataset.txt"); server.sync;


			}; // end fork

	} //--//


    //------------------//
    // START TRAINING
    //------------------//
	// Training mode: Public function used to start the training.

	starttraining { | mode = "random" |

		mode_training = mode;
		if ( is_training == False, {
			is_training = True;
			// "start training".postln;
		}, {
			"INFO: You are in training mode already".postln;
		});
		/*if (sndid_old_t.notNil && (sndid_t == sndid_old_t), {
				"Existing sound fading out...".postln;
				this.fadeout_t;
		});*/

		this.selectanswerbymode();

	} //--//


    //------------------//
    // TRAIN BY ID
    //------------------//
	// Training mode: Public function to request a sound by ID to be annotated.

	trainid { |idnumber = 3333 |

		if ( is_training == True, {
			this.givesoundbyid(idnumber);
			postln("********************************************");
			"Please wait until the sound has been downloaded before manually annotating it...".postln;
			postln("********************************************");
		}, {
			"INFO: You should call the method 'starttraining' first".postln;
		});

	} //-//

    //------------------//
    // TRAIN BY RANDOM
    //------------------//
	// Training mode: Public function to request a random sound to be annotated.

	trainrand { |idnumber = 3333 |

		if ( is_training == True, {
			this.giverandomsound();
			postln("********************************************");
			"Please wait until the sound has been downloaded before manually annotating it...".postln;
			postln("********************************************");
		}, {
			"WARNING: You should call the method 'starttraining' first".postln;
		});

	} //-//

    //------------------//
    // USER MENU TO SELECT THE TRAINING MODE
    //------------------//
	// Training mode: Private function that shows the user the available options for training.

	selectanswerbymode {

		this.messagestraining;

		// DEACTIVATED:
		/*if ( mode_training == "random", {
			// Deactivated the automatic random mode until Freesound gets fixed
			/*this.giverandomsound();
			postln("********************************************");
			"Please wait until the sound has been downloaded before manually annotating it...".postln;
			postln("********************************************");*/
		}, {
			if ( mode_training == "id", {
			postln("********************************************");
			"For training by ID, please write: trainid(xxxx) where you need to replace xxxx with the id number".postln;
			postln("********************************************");
			});
		};
		);*/
	} //-//

    //------------------//
    // CONTINUE TRAINING
    //------------------//
	// Training mode: Public function to continue training in case it has been paused or stopped

	continuetraining { | mode |
		is_training==True;
		this.starttraining( mode_training );
	} //-//


    //------------------//
	// LABEL SOUND AS 'GOOD'
    //------------------//
	// Training mode: Public function to annotate the present sound as a 'good' sound

	ok {

		if (is_training==True, {
			temp_list_training[1] = "good";
			// "sound labeled as good".postln;
			temp_list_training.postln;
			// "save sound (temp array) in a dictionary (global variable) with label good".postln;
			manual_dataset_dict.add(sndid_t.asInteger -> temp_list_training); // dictionary key = ID; dictionary value = y array
			this.updatedict("'ok'");
			this.fadeout_t;
			this.selectanswerbymode();
		}, {
			"You need to start training first".postln;
		});


	} //--//

    //------------------//
	// LABEL SOUND AS 'BAD'
    //------------------//
	// Training mode: Public function to annotate the present sound as a 'bad' sound

	ko {

		if (is_training==True, {
			temp_list_training[1] = "bad";
			// "sound labeled as bad".postln;
			temp_list_training.postln;
			// "save sound (temp array) in a dictionary (global variable) with label good".postln;
			manual_dataset_dict.add(sndid_t.asInteger -> temp_list_training); // dictionary key = ID; dictionary value = y array
			this.updatedict("'bad'");
			this.fadeout_t;
			this.selectanswerbymode();
		}, {
			"You need to start training first".postln;
		});

	} //--//

    //------------------//
	// SKIP TO LABEL THIS SOUND
    //------------------//
	// Training mode: Public function to skip adding the present sound in the training
	skip {

		if (is_training==True, {
			postln("INFO: Removing this sound from training.");
			this.messagestraining();
			this.fadeout_t;
		}, {
			postln("INFO: There's no sound to skip.\nYou need to start training first.");
		});


	} //--//

    //------------------//
	// SHOW TO THE USER INFO ABOUT THE LATEST ANNOTATION
    //------------------//
	// Training mode: Private function to show info of the latest information and the dataset

	updatedict { |value|
			postln("********************************************");
		    postln("Sound annotated as " ++ value);
			postln("You have " ++ manual_dataset_dict.size ++ " sounds in your dataset");
			postln("The sound IDs are: "++manual_dataset_dict.keys);
			postln("********************************************");
	} //-//

    //------------------//
	// SHOW INFO OF THE DATASET
    //------------------//
	// Training mode: Public function to show status information about the dataset in the training mode.

	showinfo {
		var goodsounds;
		var badsounds;
		var dataset = manual_dataset_dict;
		if ( is_training==True , {
			postln("INFO: You are in training mode.");
			postln("********************************************");
			postln("You have " ++ dataset.size ++ " sounds in your dataset");
			postln("The sound IDs are: "++ dataset.keys);
			postln("The values are: "++ dataset.values);
			dataset.values.select({ |x| x.postln; true});
			goodsounds = dataset.values.select({ |x| x[1]=="good"}).size;
			postln("Good sounds: " ++ goodsounds);
			badsounds = dataset.size - goodsounds;
			postln("Bad sounds: " ++ badsounds);
			// dataset.values.select({ |x| x[1]=='bad'}).postln;
			/*manual_dataset_dict.keysValuesDo{ |k,v|
				postln("key: "++k);
				postln("value: "++v);
				postln("value: "++v[1]);
				goodsounds = v[1].select({ |x| x[0]=='good'}).size;
				postln("Good sounds: " ++ goodsounds);
				badsounds = manual_dataset_dict.size - goodsounds;
				postln("Bad sounds: " ++ badsounds);
			};*/
			// goodsounds = manual_dataset_dict.select({ |x| x[0]=='good'}).size;
			// postln("Good sounds: " ++ goodsounds);
			// badsounds = manual_dataset_dict.size - goodsounds;
			// postln("Bad sounds: " ++ badsounds);
			postln("********************************************");
		}, {
			postln("INFO: You are in performance mode or not in training mode.");
		});
	} //-//

    //------------------//
	// PAUSE THE TRAINING
    //------------------//
	// Training mode: Public function to pause the training. This is a deprecated function since it is not needed anymore since the training by random is also manual (and not automatic as before).

	pause {
		if (is_training==True, {
			postln("INFO: Deprecated function.");
			postln("INFO: Process paused.");
			if ( sndid_old_t.notNil && (sndid_t == sndid_old_t ), {
				postln("INFO: Fading out the previous sound...");
				this.fadeout_t;
			});
		});
	} //-//

	//------------------//
    // RETURN A RANDOM SOUND
    //------------------//
	// Training mode: Private function that returns a random sound for annotation.

    giverandomsound {


		/*if (sndid_old_t.notNil && (sndid_t == sndid_old_t), {
			"Fading out the previous sound...".postln;
			this.fadeout_t;
		});*/

		// "then give a random sound".postln;
		// "its descriptors are stored in a temp array".postln;

		sndid_t = rrand (1, databaseSize);
		// sndid_t = 329706; // This SoundID is for testing "analysis":null

		this.getsoundfromfreesound (sndid_t);


    } //--//


	//------------------//
    // RETURN A SOUND BY ID
    //------------------//
	// Training mode: Private function that returns a sound by ID for annotation.

	givesoundbyid { | id = 3333 |


		/*if (sndid_old_t.notNil && (sndid_t == sndid_old_t), {
			"Fading out the previous sound...".postln;
			this.fadeout_t;
		});*/

		// "then give a sound by ID".postln;
		// "its descriptors are stored in a temp array".postln;

		sndid_t = id;
		// sndid_t = 329706; // This SoundID is for testing "analysis":null

		this.getsoundfromfreesound (sndid_t);

    } //--//

    //------------------//
    // ANALYSES A SOUND DOWNLOADED FROM FREESOUND
    //------------------//
	// Training mode: Private function used to store MFCCs values of a downloaded sound required for the training of the machine learning model.

	getsoundfromfreesound { |sndid_t = 3333 |

		backendClass.getSound ( sndid_t,
            { | f |


				var snd_t;

                snd_t = f;

				if ( snd_t.isNil,
					{
						postln("ERROR: There was a problem downloading this sound.\nWARNING: Try another sound.");
						this.messagestraining;
					},
					{
					// "INFO: The sound has been downloaded".postln;

					/*if ( snd_t.dict != nil,
						{ postln("INFO: The dict exists in Freesound" )},
						{ postln("INFO: The dict DOES NOT EXIST in Freesound") }
					);*/

					// if "analysis":null
					if (snd_t["analysis"].isNil) {
						postln("ERROR: Sound analysis does not exist.\nTry another sound.");
						this.messagestraining;
					};

					if (snd_t["detail"]=="Not found.", {
						postln("ERROR: Sound not found in the database.\nTry another sound.");
						this.messagestraining;
					});

					if ( snd_t["detail"] != "Not found." && snd_t["analysis"].notNil,
                    {
						this.prid(sndid_t, 1);

						temp_list_training = List.fill(28,0);
						temp_list_training[0] = sndid_t;

						snd_t.getAnalysis("lowlevel.mfcc", { | val |

							if ( (val.lowlevel.mfcc.mean[0] == 0) || (val.lowlevel.mfcc.mean[1] == 0) || (val.lowlevel.mfcc.mean[2] == 0) || (val.lowlevel.mfcc.mean[3] == 0) ,
								{ postln("WARNING: At least two MFCCs retrieved zero, this sound should be skipped. Please annotate it as [namevariable].skip");
							});

							temp_list_training[2] = val.lowlevel.mfcc.mean[0];
							temp_list_training[3] = val.lowlevel.mfcc.mean[1];
							temp_list_training[4] = val.lowlevel.mfcc.mean[2];
							temp_list_training[5] = val.lowlevel.mfcc.mean[3];
							temp_list_training[6] = val.lowlevel.mfcc.mean[4];
							temp_list_training[7] = val.lowlevel.mfcc.mean[5];
							temp_list_training[8] = val.lowlevel.mfcc.mean[6];
							temp_list_training[9] = val.lowlevel.mfcc.mean[7];
							temp_list_training[10] = val.lowlevel.mfcc.mean[8];
							temp_list_training[11] = val.lowlevel.mfcc.mean[9];
							temp_list_training[12] = val.lowlevel.mfcc.mean[10];
							temp_list_training[13] = val.lowlevel.mfcc.mean[11];
							temp_list_training[14] = val.lowlevel.mfcc.mean[12];
							temp_list_training[15] = val["lowlevel"]["mfcc"]["var"][0];
							temp_list_training[16] = val["lowlevel"]["mfcc"]["var"][1];
							temp_list_training[17] = val["lowlevel"]["mfcc"]["var"][2];
							temp_list_training[18] = val["lowlevel"]["mfcc"]["var"][3];
							temp_list_training[19] = val["lowlevel"]["mfcc"]["var"][4];
							temp_list_training[20] = val["lowlevel"]["mfcc"]["var"][5];
							temp_list_training[21] = val["lowlevel"]["mfcc"]["var"][6];
							temp_list_training[22] = val["lowlevel"]["mfcc"]["var"][7];
							temp_list_training[23] = val["lowlevel"]["mfcc"]["var"][8];
							temp_list_training[24] = val["lowlevel"]["mfcc"]["var"][9];
							temp_list_training[25] = val["lowlevel"]["mfcc"]["var"][10];
							temp_list_training[26] = val["lowlevel"]["mfcc"]["var"][11];
							temp_list_training[27] = val["lowlevel"]["mfcc"]["var"][12];

							// temp_list_training.postln;

						}, true);

						// "INFO: sound descriptors are downloaded".postln;
						sndid_old_t = sndid_t;

                    },
                    {
						postln("ERROR: Either SoundID or sound analysis does not exist.\nWARNING: Try another sound.");
						this.messagestraining;
					}); // End IF getanalysis
				}); // End of IF snd_t.isNil
        }); // End of backendClass.getSound
	} //-//

	//------------------//
    // PRINT MESSAGES TO USER
    //------------------//
	// Training mode: Private function that reminds the user about available methods for training

	messagestraining {

		if (is_training==True, {

			postln("********************************************");
			"You are in training mode.".postln;
			"You need to request for a new sound and then tell if you like it or not.".postln;
			postln("(1)");
			"To get a new random sound...".postln;
			"Please write: [namevariable].trainrand".postln;
			"To get a new sound by ID...".postln;
			"Please write: [namevariable].trainid(xxxx)".postln;
			"where you need to replace xxxx with the Freesound ID number".postln;
			postln("(2)");
			"Please wait until the sound has been downloaded before manually annotating it...".postln;
			"If you like the sound, please write: [namevariable].ok".postln;
			"If you don't like the sound, please write: [namevariable].ko".postln;
			"If you want to skip the sound, please write: [namevariable].skip".postln;
			postln("********************************************");
		});

	}

	//------------------//
    // STOP TRAINING
    //------------------//
	// Training mode: Public function to stop training the dataset.

	stoptraining { |perc = 0.7 |

		var result = Array.new;
		var training_candidates;
		var training_dataset_content = Dictionary.new;
		var test_dataset_content = Dictionary.new;
		var training_labels_content = Dictionary.new;
		var test_labels_content = Dictionary.new;
		var num_valid;

		if ( is_training == True, {
			is_training = False;
		});

		/*if (sndid_old_t.notNil && (sndid_t == sndid_old_t), {
			"Fading out the previous sound...".postln;
			this.fadeout_t;
		});*/

		training_candidates = (manual_dataset_dict.size*perc).round.asInteger;
		postln("********************************************");
		postln("There are: "++training_candidates++" training_candidates");
		postln("There are: "++(manual_dataset_dict.size - training_candidates)++" testing_candidates");
		postln("********************************************");

		manual_dataset_dict.keysDo { |key, count|
			if( count < training_candidates, {
				// add to train
				result = manual_dataset_dict.at(key);
				training_dict.add(key -> result[2..27].asFloat); // number of inputs
				training_label_dict.add(key -> result[1].asSymbol.asArray);
			},{
				// add to test
				result = manual_dataset_dict.at(key);
				test_dict.add(key -> result[2..27].asFloat); // number of inputs
				test_label_dict.add(key -> result[1].asSymbol.asArray);
			});

		};

		// prepare data

		training_dataset_content.add(\cols -> 26); // number of inputs
		training_dataset_content.add(\data -> training_dict);

		test_dataset_content.add(\cols -> 26); // number of inputs
		test_dataset_content.add(\data -> test_dict);


		//prepare labels

		training_labels_content.add(\cols -> 1);
		training_labels_content.add(\data -> training_label_dict);

		test_labels_content.add(\cols -> 1);
		test_labels_content.add(\data -> test_label_dict);

		//load

		fork {
			//for training
			training_dataset.load(training_dataset_content); server.sync;
			training_dataset.dump; server.sync;
			training_label_dataset.load(training_labels_content); server.sync;
			training_label_dataset.dump; server.sync;
			//same for test
			test_dataset_fixed.load(test_dataset_content); server.sync;
			test_dataset_fixed.dump; server.sync;
			test_label_dataset_fixed.load(test_labels_content); server.sync;
			test_label_dataset_fixed.dump; server.sync;
			// transform training
			standardizer.fitTransform(training_dataset, stand_dataset, {"Standardizer training Done".postln;});
			server.sync;
			// transform test
			standardizer.transform(test_dataset_fixed, stand_test_dataset_fixed, {"Standardizer training Done".postln;});
			server.sync;
			// transform training
			pca.fitTransform(stand_dataset, stand_dataset, {"PCA training Done".postln;});
			server.sync;
			//transfrom test
			pca.transform(stand_test_dataset_fixed, stand_test_dataset_fixed, {"PCA test Done".postln;});
			server.sync;
			// classifier fit
			classifier.fit(stand_dataset, training_label_dataset, action:{|loss| ("Trained"+loss).postln});
			server.sync;
			// classifier predict
			classifier.predict(stand_test_dataset_fixed, test_predicted_label_dataset, action:{"Test complete".postln});
			server.sync;
			// accuracy
			num_valid = 0;
			// "***ACCURACY****".postln;
			test_label_dict.size.postln;
			test_label_dict.keysValuesDo{ |k,v|
				// postln("key: "++k);
				// postln("value: "++v);
				test_predicted_label_dataset.getLabel(k, { |l|
					// postln("predicted: "++l);
					if(l==v[0].asString){
						num_valid = num_valid+1};
				});
				server.sync;
			};
			postln("********************************************");
			postln("Accuracy (0%-100%): " ++ ( (num_valid/test_label_dict.size) * 100) ++ "%");
			postln("Continue training or Save to JSON files?");
			postln("To continue training: [nameVariable].continuetraining");
			postln("To save to JSON files: [nameVariable].save");
			postln("********************************************");
		};

	} //--//

    //------------------//
    // SAVE TRAINING DATA
    //------------------//
	// Training mode: Public function to save the machine learning model as JSON files

	save {

		try {
			try {
				classifier.write( modelfilepath ++ "model-new.JSON" );
			}
			{ |error| [\catchFileWriteModel, error].postln }; // end catch error

			try {
				standardizer.write( modelfilepath ++ "standardizer-new.JSON");
			}
			{ |error| [\catchFileWriteStandardizer, error].postln }; // end catch error

			try {
				pca.write( modelfilepath ++ "pca-new.JSON");
			}
			{ |error| [\catchFileWritePCA, error].postln }; // end catch error

			if ( is_training == True, {
				is_training = False;
			});

			postln("INFO: The JSON files of your machine learning model have been saved.");
		}


	} //--//

    //------------------//
    // ARCHIVE TRAINING DATA FOR LATER USE
    //------------------//
	// Training mode: Public function to save the machine learning model as JSON files for a later follow-up.

	archive {
		postln("INFO: This function will archive the dictionary for future training.");
		postln(manual_dataset_dict);
	} //-//

	load {
		postln("INFO: This function will load the dictionary for follow-up training.");
		postln("********************************************");
		postln("You have " ++ manual_dataset_dict.size ++ " sounds in your dataset");
		postln("The sound IDs are: "++manual_dataset_dict.keys);
		postln("********************************************");
	}

	fadeout_t { |release = 1.0|

		this.fadeout;

	}

    //------------------//
    // RETRIEVE SOUNDS
    //------------------//
    // Training mode: Private function that has been modified to only deal with one sound at a time for the training.
	// It manages the dictionary metadata (sounds with Freesound sound info) and loads the new sound.
    // It stores a group of one sound that is stored in index 1.

    loadmetadata_t { |totalsnds = 1|
        totalsnds.do ({ |index|
            this.loadsounds(metadata, index);
        });
		this.printmetadata;
    }


}

