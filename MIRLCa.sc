//
// This class inherits from MIRLCRep2
// You will need the following JSON files stored in the same path than MIRLCRep2 generated using the FluCoMa library:
// - model.JSON
// - standardizer.JSON
// - pca.JSON
//
// Refer to MIRLCRep2 for the following setup changes:
// @new users, control the following customizable vars:
// - Freesound.token = "<your_api_key>"
//
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
	var snd_t;
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

	// change back to path = "Platform.defaultTempDir"
    *new {|backend = 0, dbSize = 478456, path = "/Users/anna/Desktop/MIRLCa/"|
		^super.new(backend, dbSize, path).initagent;
    }

	initagent {
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

		is_training = False;
		manual_dataset_dict = Dictionary.new;
		training_dict = Dictionary();
		training_label_dict = Dictionary();
		test_dict = Dictionary();
		test_label_dict = Dictionary();

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
		standardizer.read(directoryPath ++ "standardizer.JSON");
		pca.read(directoryPath ++ "pca.JSON");
		classifier.read(directoryPath ++ "model.JSON");
		};

		};

	}

    //------------------//
    // GET SOUND BY ID
    //------------------//
    // This function can be used as a standalone public function to get [1..n] sounds by ID, and it is also used as a private function by random, tag, similar, filter, content to get sounds
    // params: id, size
	// Overwriting this function to avoid sounds without analysis information
    id { |id = 31362, size = 1|

		try {
        backendClass.getSound(id,
            { |f|
                //available metadata: "id","url","name","tags","description","geotag","created","license","type","channels","filesize""bitrate","bitdepth","duration","samplerate","username","Jovica","pack","pack_name","download","bookmark","previews","preview-lq-mp3","preview-hq-ogg","preview-hq-mp3","images","num_downloads","avg_rating","num_ratings","rate":,"comments","num_comments","comment","similar_sounds","analysis","analysis_frames","analysis_stats"

				snd = f;

				if (snd["detail"]=="Not found.", {
					"Sound not found in the database. Try another sound.".postln;
				}, {

				// if the sound exists in the Fressound database...

				if (is_training == False,
				{
				// sndid_t = 329706; // This SoundID is for testing "similar_sounds":null
				if ( snd["similar_sounds"].isNil , {
					postln ("This sound does not have similar_sounds information required by the MIRLCa agent. Please try another sound.");
				}, {

					index = metadata.size;
					try { // try 2
						file.write(snd["name"] + " by " + snd["username"] + snd["url"] + "\n");
					}//end try 2
					{ |error| [\catchFileWrite, error].postln };
					metadata.add(index -> f);

					if (size == 1, {
						this.loadmetadata(size);
					},{ // size > 1
						if ( (metadata.size - poolsizeold) == size, // need to wait until asynchronous call is ready! once all sounds are added in the dictionary, they can be retrieved
							{
								this.loadmetadata(size);
							}
						);
					});

				} );

			},
			{
				// Training is TRUE, only 1 sound at a time
				index = 0;
				// TODO: Write the files during training distinguishing	between good and bad sounds
				/*try {
					file.write(snd["name"] + " by " + snd["username"] + snd["url"] + "\n");
				}//end try
				{ |error| [\catchRandomMethod, error].postln }; // end catch error	*/
				metadata.add(index -> f);
				this.loadmetadata_t(1); // size = 1, only 1 sound at a time
			}
		);

		});

        } ); // end backendClass.getSound
		}//end try
		{|error| [\catchIDmethod, error].postln };
    } //--//

    //------------------//
    // SIMILAR SOUNDS
    //------------------//
    // This function gets [1..n] similar sounds from a target sound, usually the first sound from the dictionary

	// temp solution: it returns one similar sound for now
	similar { | targetnumsnd = 0, size = 1, cand = 14 |

		var index_offset = 1; //*
		var candidates = cand; // 1 page has 15 sounds. %TODO: load a second page asynchronously using FSPager as an asynchronous action using the method next
		var query_params = Dictionary[
			"fields"->"id,name,analysis",
			"descriptors"->"lowlevel.mfcc"
		];

		target = metadata[targetnumsnd];  // before: metadata[targetnumsnd - 1];

		// temp solutions to avoid unhandled limits
		if (targetnumsnd >= metadata.size, {"This sound does not exist, choose another target sound.".postln; }); // temp solution: limit retrieval to 1 sound
		if (size != 1, {size = 1}); // temp solution: limit retrieval to 1 sound
		if (candidates > 14, {candidates = 14}); // temp solution: limit to 14 sounds provided by FSPager

		try {
		target.getSimilar(params:query_params,
			action: { |p|

		/*// Getting info lf the incoming sound
		p[i].id.postln;
		p[i].dict.keys.postln;
		p[i].dict.values.postln;*/

		this.getbestfromtarget(p, candidates, index_offset);

		});
		}//end try
		{|error| [\catchSimilarMethod, error].postln };

	} //--//

    //------------------//
    // QUERY BY TAG
    //------------------//
    // This function gets [1..n] sounds by one defined tag, and plays them

	// temp solution: it returns one similar sound for now

    tag { | tag = "noise", size = 1, cand = 15 |

		var index_offset = 0; //*
		var candidates = cand; // 1 page has 15 sounds. %TODO: load a second page asynchronously using FSPager as an asynchronous action using the method next
		var query_params = Dictionary[
			"fields"->"id,name,analysis",
			"descriptors"->"lowlevel.mfcc"
		];

		if (size != 1, {size = 1}); // temp solution: limit retrieval to 1 sound
		if (candidates > 15, {candidates = 15}); // temp solution: limit to 15 sounds provided by FSPager

		try {
        backendClass.textSearch(query: tag, params: query_params,
            action: { |p|

				this.getbestfromtarget(p, candidates, index_offset);

               /* size.do { |index|
                    snd = p[index];
                    postln("found sound by tag, id: " ++ snd["id"] ++ "name: " ++ snd["name"]);
					while (
						{this.sndidexist(snd.id) == 1},
						{
							postln ("repeated sound, getting another sound...");
							index = index + 1 + size;
							snd = p[index];
					});
                this.id(snd.id, 1); // so that each sound is loaded & directly played
                }*/
		    });
			}//end try
		{|error| [\catchTagMethod, error].postln };
    } //--//

    //------------------//
    // QUERY BY RANDOM
    //------------------//
    // This function gets [1..n] sounds by random, and plays them

	// temp solution: it returns one similar sound for now
    random { | size = 1, cand = 15 |

		var sndid;

        if ( debugging == True, {postln("Sounds selected by random: " ++ size);} );

		sndid = this.getbest(4);
		// sndid = rrand (1, databaseSize);

/*		try {
        backendClass.getSound ( sndid,
            { |f|

                snd = f;

				try { //try2
                if ( snd["detail"]=="Not found.",
                    {
						if ( debugging == True, {"SoundID does not exist".postln;} );
						"Sound not found in the database. Getting another sound.".postln;
                        this.random(size);



                    },
                    {
                        if ( debugging == True, {
                            postln("potential sound candidate: ");
                            snd["name"].postln;
							postln("counter value is: " + counter);
                        });

                        counter = counter + 1; // adding one sound to the counter in a recursive fashion
                        if (size == 1,
                            {
                                this.id(sndid, size);
                            },
                            {//size > 1
								if ( debugging == True, {
									this.id(sndid, size);
									postln("group size is greater than 1");
									postln("( counter - size ): " ++ ( counter - size ));
									}); // end if debugging

                                if ( counter <= size ,
                                    {
                                        this.id(sndid, size);
                                        if ( counter < size, { this.random(size); } );
									}
                                );
                            }
                        );

                } );
				}//end try2
				{|error| [\catchRandomMethod_snd, error].postln };
        } );
		}//end try
		{|error| [\catchRandomMethod, error].postln };*/

    } //--//

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
			temp_list_ids.postln;
		});

		// 2) get the analysis

		fork {
		candidates.do { | index |

			5.wait; // This is the amount of time in sec. For example, 10 sec means 6 sounds per minute.
			cond1.test = false;
			cond2.test = false;

			sndid = temp_list_ids[index];
			sndid.postln;
			// sndid = 3333; // for debugging purposes - 1 sound
			 if ( debugging == True, {
				// snd.dict.keys.postln;
				// snd.dict.values.postln;
				("Current analysed sound from the list of FS similar sounds").postln;
				snd.dict["id"].postln;
			});

			// Check if the sound exists, if NOT do nothing

			try {
			backendClass.getSound ( sndid,
			{ |f|
			var tmpSnd;
			// ("getSound"+sndid+" "+f.id.asInteger).postln;
			temp_list = List.new;
			cond1.wait;
			tmpSnd = f;
			"sound is downloaded".postln;

			// Extra comment:
			// Nicer and more compact code, but unfortunately it cannot be used because we need to keep the same order than in the training process.
			/*13.do{| i |
						temp_list.add(snd.analysis.lowlevel.mfcc.mean[i]);
				temp_list.add(snd["analysis"]["lowlevel"]["mfcc"]["var"][i]);
			};*/
			if (tmpSnd["analysis"].isNil) {
					"Sound analysis does not exist".postln;
					// snd_t["analysis"].postln;
				};

			if (tmpSnd["detail"]=="Not found.", { // TODO: For some reason this does not seem to work: If the sound does not exist it retrieves the message "ERROR: Message 'id' not understood."
				"Sound not found in the database. Sound not included.".postln;
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
				("Storing MFCC information in a temp list, for each sound").postln;
				"temp_list:  ".postln;
				// temp_list.size.postln;
				temp_list.postln;
				// temp_dict.postln;
				// temp_dict.size.postln;
			});

			// ("adding"+tmpSnd.id.asInteger).postln;
			temp_dict.add(tmpSnd.id.asInteger -> temp_list.asFloat); // dictionary key = ID; dictionary value = array

			cond2.wait;
			"sound descriptors are downloaded".postln;

			if ( debugging == True, {
				temp_dict.postln;
				temp_dict.keys.postln;
				temp_dict.size.postln;
			});

			}, true); // end snd.getanalysis

			// "sound descriptors are downloaded".postln;

			}); // end if

		temp_counter = temp_counter + 1;

		}); // End of backendClass.getSound
			cond1.test = true;
			cond1.signal;
			cond2.test = true;
			cond2.signal;


		}//end try
		{|error| [\catchgetSoundMethod, error].postln };

		}; // End of size.do loop
			"Done".postln;

			// 3) evaluate which one is the best ID

			test_dataset_content = Dictionary.new;
			test_dataset_content.add(\cols -> 26); // number of inputs
			test_dataset_content.add(\data -> temp_dict);

			if ( debugging == True, {
				"test_dataset_content!!!!!!!!!!".postln;
				test_dataset_content.postln;
				test_dataset_content.[\data].postln;
				test_dataset_content.size.postln;
			});

			// server.sync;
			test_dataset.load(test_dataset_content); server.sync;
			test_dataset.dump; server.sync;
			// test_dataset.write(directoryPath ++ "mirlca_test_dataset.txt"); server.sync;
			standardizer.transform(test_dataset, stand_test_dataset); server.sync;
			// stand_test_dataset.write(directoryPath ++ "mirlca_standardizer.txt"); server.sync;
			stand_test_dataset.dump; server.sync;
			pca.transform(stand_test_dataset, stand_test_dataset, {
				"Done".postln;
			}); server.sync;
			stand_test_dataset.dump; server.sync;
			// stand_test_dataset.write(directoryPath ++ "mirlca_pca.txt"); server.sync;
			classifier.predict(stand_test_dataset, test_predicted_label_dataset, action:{"Test complete".postln}); server.sync;
			// test_predicted_label_dataset.dump;
			test_predicted_label_dataset.dump(action:{ |dict|
				var found = False;
				dict["data"].keysValuesDo{ |k,v|

				// 4) return the sound
				if ( v[0] =="good" && found == False, {
					k.postln;
					this.id(k, 1); // this function comes from getbestfromtarget, which is not useful here as we need to return the id number instead
					// return the ID
					"MIRLCa: Do you like this sound?".postln;
					found = True;
				});

				};
				if (found == False, {("Only bad sounds were found from "++(candidates-1)++" candidates. Try another sound.").postln});
				});
				test_predicted_label_dataset.write(directoryPath ++ "test_predicted_label_dataset.txt"); server.sync;

		}; // fork



		/*fork {
			// server.sync;
			test_dataset.load(test_dataset_content); server.sync;
			test_dataset.dump; server.sync;
			// test_dataset.write(directoryPath ++ "mirlca_test_dataset.txt"); server.sync;
			standardizer.transform(test_dataset, stand_test_dataset); server.sync;
			// stand_test_dataset.write(directoryPath ++ "mirlca_standardizer.txt"); server.sync;
			stand_test_dataset.dump; server.sync;
			pca.transform(stand_test_dataset, stand_test_dataset, {
				"Done".postln;
			}); server.sync;
			stand_test_dataset.dump; server.sync;
			// stand_test_dataset.write(directoryPath ++ "mirlca_pca.txt"); server.sync;
			classifier.predict(stand_test_dataset, test_predicted_label_dataset, action:{"Test complete".postln}); server.sync;
			// test_predicted_label_dataset.dump;
			test_predicted_label_dataset.dump(action:{ |dict|
				var found = False;
				dict["data"].keysValuesDo{ |k,v|

				// size.do { // TODO: implementthe retrieval of +1 sound
				if ( v[0] =="good" && found == False, {
					k.postln;
					// this.id(k, 1); // this function comes from getbestfromtarget, which is not useful here as we need to return the id number instead
					// return the ID
					result = k;
					"MIRLCa: Do you like this sound?".postln;
					found = True;
				});

				// }; // End of size.do loop

					//k.postln;
					//v.postln;
					//v.class.postln;
				};
				if (found == False, {("Only bad sounds were found from "++(candidates-1)++" candidates. Try another sound.").postln});
				});
				test_predicted_label_dataset.write(directoryPath ++ "test_predicted_label_dataset.txt"); server.sync;


			};*/ // end fork

			// 4) return the ID
		// result = temp_list[0];
		// ^result;






	} //--//

	getbestfromtarget { |p, candidates, index_offset = 0|

		var temp_list = List.new; //*
		var temp_dict = Dictionary.new; //*
		var test_dataset_content = Dictionary.new; //*
		var temp_counter = 0; //*

		candidates.do { |index|

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
							postln ("repeated sound, getting another sound...");
					});
			});

			 if ( debugging == True, {
				// snd.dict.keys.postln;
				// snd.dict.values.postln;
				("Current analysed sound from the list of FS similar sounds").postln;
				snd.dict["id"].postln;
				temp_list = List.new;
			});

			// Extra comment:
			// Nicer and more compact code, but unfortunately it cannot be used because we need to keep the same order than in the training process.
			/*13.do{| i |
						temp_list.add(snd.analysis.lowlevel.mfcc.mean[i]);
				temp_list.add(snd["analysis"]["lowlevel"]["mfcc"]["var"][i]);
			};*/

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
			("Storing MFCC information in a temp list, for each sound").postln;
			"temp_list:  ".postln;
			// temp_list.size.postln;
			temp_list.postln;
			});

			temp_dict.add(snd.id.asInteger -> temp_list.asFloat); // dictionary key = ID; dictionary value = array

			// if ( debugging == True, {
			// temp_dict.postln;
			// temp_dict.size.postln;
			// };

			// Original code from MIRLC, the sound is retrieved here
			// this.id(snd.id, 1); // so that each sound is loaded directly played

		}; // End of size.do loop

		test_dataset_content = Dictionary.new;
		test_dataset_content.add(\cols -> 26); // number of inputs
		test_dataset_content.add(\data -> temp_dict);
		"test_dataset_content".postln;
		test_dataset_content.postln;

		fork {
			// server.sync;
			test_dataset.load(test_dataset_content); server.sync;
			test_dataset.dump; server.sync;
			// test_dataset.write(directoryPath ++ "mirlca_test_dataset.txt"); server.sync;
			standardizer.transform(test_dataset, stand_test_dataset); server.sync;
			// stand_test_dataset.write(directoryPath ++ "mirlca_standardizer.txt"); server.sync;
			stand_test_dataset.dump; server.sync;
			pca.transform(stand_test_dataset, stand_test_dataset, {
				"Done".postln;
			}); server.sync;
			stand_test_dataset.dump; server.sync;
			// stand_test_dataset.write(directoryPath ++ "mirlca_pca.txt"); server.sync;
			classifier.predict(stand_test_dataset, test_predicted_label_dataset, action:{"Test complete".postln}); server.sync;
			// test_predicted_label_dataset.dump;
			test_predicted_label_dataset.dump(action:{ |dict|
				var found = False;
				dict["data"].keysValuesDo{ |k,v|

				// size.do { // TODO: implementthe retrieval of +1 sound
				if ( v[0] =="good" && found == False, {
					k.postln;
					this.id(k, 1); // so that each sound is loaded directly played
					"MIRLCa: Do you like this sound?".postln;
					found = True;
				});

				// }; // End of size.do loop

					//k.postln;
					//v.postln;
					//v.class.postln;
				};
				if (found == False, {("Only bad sounds were found from "++(candidates-1)++" candidates. Try another sound.").postln});
				});
				test_predicted_label_dataset.write(directoryPath ++ "test_predicted_label_dataset.txt"); server.sync;


			}; // end fork

	} //--//


	starttraining { | mode = "random" |

		mode_training = mode;
		if ( is_training == False, {
			is_training = True;
			// "start training".postln;
		}, {
			// "you are training already".postln;
		});
		if (sndid_old_t.notNil && (sndid_t == sndid_old_t), {
				"Existing sound fading out...".postln;
				this.fadeout_t;
		});

		this.selectanswerbymode();

	} //--//

	trainid { |idnumber = 3333 |
		this.givesoundbyid(idnumber);
		postln("********************************************");
		"Please wait until the sound has been downloaded before manually annotating it...".postln;
		postln("********************************************");
	}

	selectanswerbymode {
		if ( mode_training == "random", {
			// "give a random sound".postln;
			this.giverandomsound();
			postln("********************************************");
			"Please wait until the sound has been downloaded before manually annotating it...".postln;
			postln("********************************************");

		}, {
			postln("********************************************");
			"For the next sound: Please write: trainid(xxxx) where you need to replace xxxx with the id number".postln;
			postln("********************************************");
			// "Please wait until the sound has been downloaded before manually annotating it...".postln;
			// postln("********************************************");
		};
		);
	}

	continuetraining {
		this.starttraining( mode_training );
	}


	ok {

		if (is_training==True, {
			temp_list_training[1] = "good";
			// "sound labeled as good".postln;
			temp_list_training.postln;
			// "save sound (temp array) in a dictionary (global variable) with label good".postln;
			manual_dataset_dict.add(sndid_t.asInteger -> temp_list_training); // dictionary key = ID; dictionary value = y array
			postln("********************************************");
			postln("You have " ++ manual_dataset_dict.size ++ " sounds in your dataset");
			postln("The sound IDs are: "++manual_dataset_dict.keys);
			postln("********************************************");
			this.selectanswerbymode();
		}, {
			"You need to start training first".postln;
		});


	} //--//

	ko {

		if (is_training==True, {
			temp_list_training[1] = "bad";
			// "sound labeled as bad".postln;
			temp_list_training.postln;
			// "save sound (temp array) in a dictionary (global variable) with label good".postln;
			manual_dataset_dict.add(sndid_t.asInteger -> temp_list_training); // dictionary key = ID; dictionary value = y array
			postln("********************************************");
			postln("You have " ++ manual_dataset_dict.size ++ " sounds in your dataset");
			postln("The sound IDs are: "++manual_dataset_dict.keys);
			postln("********************************************");
			this.selectanswerbymode();
		}, {
			"You need to start training first".postln;
		});

	} //--//

	pause {
		"Process paused.".postln;
		if (sndid_old_t.notNil && (sndid_t == sndid_old_t), {
				"Fading out the previous sound...".postln;
				this.fadeout_t;
		});
	}

	//------------------//
    // QUERY BY RANDOM
    //------------------//
	// This function gets 1 sound by random, and plays it (ONLY for training)
    giverandomsound {


		if (sndid_old_t.notNil && (sndid_t == sndid_old_t), {
			"Fading out the previous sound...".postln;
			this.fadeout_t;
		});

		// "then give a random sound".postln;
		// "its descriptors are stored in a temp array".postln;

		sndid_t = rrand (1, databaseSize);
		// sndid_t = 329706; // This SoundID is for testing "analysis":null

		this.getsoundfromfreesound (sndid_t);


    } //--//


	//------------------//
    // QUERY BY ID
    //------------------//
	// This function gets 1 sound by ID, and plays it (ONLY for training)
	givesoundbyid { | id = 3333 |


		if (sndid_old_t.notNil && (sndid_t == sndid_old_t), {
			"Fading out the previous sound...".postln;
			this.fadeout_t;
		});

		// "then give a sound by ID".postln;
		// "its descriptors are stored in a temp array".postln;

		sndid_t = id;
		// sndid_t = 329706; // This SoundID is for testing "analysis":null

		this.getsoundfromfreesound (sndid_t);

    } //--//

	getsoundfromfreesound { |sndid_t = 3333 |

        backendClass.getSound ( sndid_t,
            { |f|

				// "this random sound is stored in a global snd variable".postln;
                snd_t = f;
				// "sound is downloaded".postln;

				// if "analysis":null
				if (snd_t["analysis"].isNil) {
					"Sound analysis does not exist".postln;
					// snd_t["analysis"].postln;
				};

				if (snd["detail"]=="Not found.", {
					"Sound not found in the database. Try another sound.".postln;
				});

                 if ( snd_t["detail"] != "Not found." && snd_t["analysis"].notNil,
                    {
						this.id(sndid_t, 1);

						/*snd_t.getAnalysis( "lowlevel.pitch", {|val|
            val.lowlevel.pitch.mean.postln;
						}, true);*/

						temp_list_training = List.fill(28,0);
						temp_list_training[0] = sndid_t;

						snd_t.getAnalysis("lowlevel.mfcc", { |val|
							// val.postln;

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

						// "sound descriptors are downloaded".postln;
						sndid_old_t = sndid_t;

                    },
                    {
                        "Either SoundID or sound analysis does not exist".postln;

						if (mode_training == "random", {
							"I'm getting another sound...".postln;
							this.giverandomsound();
						},{
							"You should get another sound...".postln;
							"Please write: trainid(xxxx) where you need to replace xxxx with the id number".postln;
						}
						);

                } );

        } );
	}


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

		if (sndid_old_t.notNil && (sndid_t == sndid_old_t), {
			"Fading out the previous sound...".postln;
			this.fadeout_t;
		});

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
			postln("Accuracy (0%-100%): "++(num_valid/test_label_dict.size));
			postln("Continue training or Save to JSON files?");
			postln("********************************************");
		};

	} //--//

	save {
		classifier.write(directoryPath ++ "model-new.JSON");
		standardizer.write(directoryPath ++ "standardizer-new.JSON");
		pca.write(directoryPath ++ "pca-new.JSON");
		// TODO: training is now false

	} //--//

	fadeout_t { |release = 1.0|

		this.fadeout;

	}

    //------------------//
    // RETRIEVE SOUNDS
    //------------------//
    // This function has been modified to only deal with one sound at a time for the training.
	// It manages the dictionary metadata (sounds with fs info) and loads the new sound.
    // It stores a group of one sound that is stored in index 1.
    loadmetadata_t { |totalsnds = 1|
        totalsnds.do ({ |index|
            this.loadsounds(metadata, index);
        });
		this.printmetadata;
    }


}

