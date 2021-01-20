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
// - path: replace current directory to your own directory to store downloaded sounds and record the text file with the credits, or change it to "/tmp/"
// - debugging: True/False


MIRLCa : MIRLCRep2 {

	var test_dataset;
	var stand_test_dataset;
	var standardizer;
	var pca;
	var classifier;
	var test_predicted_label_dataset;
	var snd_t;
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


    *new {|backend = 0, dbSize = 478456, path = "Platform.defaultTempDir"|
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

        backendClass.getSound(id,
            { |f|
                //available metadata: "id","url","name","tags","description","geotag","created","license","type","channels","filesize""bitrate","bitdepth","duration","samplerate","username","Jovica","pack","pack_name","download","bookmark","previews","preview-lq-mp3","preview-hq-ogg","preview-hq-mp3","images","num_downloads","avg_rating","num_ratings","rate":,"comments","num_comments","comment","similar_sounds","analysis","analysis_frames","analysis_stats"

				snd = f;

				if (is_training == False,
					{
						// sndid_t = 329706; // This SoundID is for testing "similar_sounds":null
						if ( snd["similar_sounds"].isNil , {
							postln ("This sound does not have similar_sounds information. Please try another sound.");
						}, {

							index = metadata.size;
							file.write(snd["name"] + " by " + snd["username"] + snd["url"] + "\n");

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
						file.write(snd["name"] + " by " + snd["username"] + snd["url"] + "\n");
						metadata.add(index -> f);
						this.loadmetadata_t(1); // size = 1, only 1 sound at a time
					}
				)



        } );
    } //--//


	similar { | targetnumsnd = 0, size = 10 |
        var newSnd;
		var temp_list = List.new;
		var temp_dict = Dictionary.new;
		var test_dataset_content = Dictionary.new;
		var query_params = Dictionary[
			"fields"->"id,name,analysis",
			"descriptors"->"lowlevel.mfcc"
		];
		var index_offset = 0;
		var counter = 0;

		target = metadata[targetnumsnd];  // before: metadata[targetnumsnd - 1];

		target.getSimilar(params:query_params,
			action: { |p|

				// list of 10 candidate sounds + p[0] that it is identity (same sound), just in case some sounds are repeated or the sounds are already in the bucket
				11.do{ |i|
					// p[i].dict.keys.postln;
					p[i].id.postln;
					// p[i].dict.values.postln;
				};

				if(size != 10, {size = 10}); // temp hack to make sure we select the best candidate from a set of 10 audio samples

				size.do { |index|
					index_offset = index_offset + 1;
					snd = p[index_offset];

					// check if snd.id already exists, if so, take next sound
					// this needs to be revised to keep a window frame of 10 sounds
					if ( metadata.size > 0,
						{
							counter = 0;
							while ( { this.sndidexist(snd.id) == 1 },
								{
									counter = counter + 1;
									index_offset = index_offset + counter;
									snd = p[index_offset];
									postln ("repeated sound, getting another sound...");
							});
					});

					snd.dict.keys.postln;
					// snd.dict.values.postln;
					snd.dict["id"].postln;

					temp_list = List.new;

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

					"temp_list:  ".postln;
					temp_list.size.postln;
					temp_list.postln;
					temp_dict.add(snd.id.asInteger -> temp_list.asFloat); // dictionary key = ID; dictionary value = array
					// temp_dict.postln;
					// temp_dict.size.postln;

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
					test_dataset.write(directoryPath ++ "mirlca_test_dataset.txt"); server.sync;
					standardizer.transform(test_dataset, stand_test_dataset); server.sync;
					stand_test_dataset.write(directoryPath ++ "mirlca_standardizer.txt"); server.sync;
					stand_test_dataset.dump; server.sync;
					pca.transform(stand_test_dataset, stand_test_dataset, {
						"Done".postln;
					}); server.sync;
					stand_test_dataset.dump; server.sync;
					stand_test_dataset.write(directoryPath ++ "mirlca_pca.txt"); server.sync;
					classifier.predict(stand_test_dataset, test_predicted_label_dataset, action:{"Test complete".postln}); server.sync;
					// test_predicted_label_dataset.dump;
					test_predicted_label_dataset.dump(action:{ |dict|
						var found = False;
						dict["data"].keysValuesDo{ |k,v|
							if ( v[0] =="good" && found == False, {
								k.postln;
								this.id(k, 1); // so that each sound is loaded directly played
								"MIRLCa: Do you like this sound?".postln;
								found = True;
							});
							//k.postln;
							//v.postln;
							//v.class.postln;
						};
						if (found == False, {"Only bad sounds were found.".postln});
					});
					test_predicted_label_dataset.write(directoryPath ++ "test_predicted_label_dataset.txt"); server.sync;




				};

		});

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

                 if ( snd_t["detail"] == nil && snd_t["analysis"].notNil,
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

