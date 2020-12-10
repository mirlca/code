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
// - path: replace current directory to your own directory to store downloaded sounds and record the text file with the credits, or change it to "/tmp/"
// - debugging: True/False


MIRLCa : MIRLCRep2 {


	var test_dataset;
	var stand_test_dataset;
	var standardizer;
	var pca;
	var classifier;
	var test_predicted_label_dataset;

    *new {|backend = 0, dbSize = 478456, path = "/Users/anna/Desktop/MIRLC/"|
		^super.new(backend, dbSize, path).initagent;
    }

	initagent {
		var randomnum = 100000.rand;
		var test_dataset_name = \mlpclassify_testdata++randomnum.asSymbol;
		var stand_test_dataset_name = \mlpclassify_stand_test_data++randomnum.asSymbol;
		var test_predicted_label_dataset_name = \mlpclassify_predictlabels++randomnum.asSymbol;

		server.waitForBoot {

		"I'm an agent".postln;

		fork {
		test_dataset.free;
		test_dataset = FluidDataSet(server,test_dataset_name);
		stand_test_dataset.free;
		stand_test_dataset = FluidDataSet(server,stand_test_dataset_name);
		test_predicted_label_dataset.free;
		test_predicted_label_dataset = FluidLabelSet(server,test_predicted_label_dataset_name);
		standardizer.free;
		standardizer = FluidStandardize(server);
		pca.free;
		pca = FluidPCA(server,20);
		classifier.free;
		classifier = FluidMLPClassifier(server, [14], FluidMLPClassifier.relu, 10000, 0.01, 0.9, 10, 0.2);
		server.sync;
		standardizer.read(directoryPath ++ "standardizer.JSON");
		pca.read(directoryPath ++ "pca.JSON");
		classifier.read(directoryPath ++ "model.JSON");
		};

		};

	}


	similar { | targetnumsnd = 0, size = 10 |
        var newSnd;
		var temp_list = List.new;
		var temp_dict = Dictionary.new;
		var test_dataset_content = Dictionary.new;
		var query_params = Dictionary[
			"fields"->"id,name,analysis",
			"descriptors"->"lowlevel.mfcc"
		];

		target = metadata[targetnumsnd];  // before: metadata[targetnumsnd - 1];

		target.getSimilar(params:query_params,
			action: { |p|
				p[0].dict.keys.postln;
				p[0].id.postln;
				//p[0].dict.values.postln; // The most original sound is itself
				p[1].dict.keys.postln;
				p[1].id.postln;
				//p[1].dict.values.postln; // The next most similar sound in the list

				if(size != 10, {size = 10}); // temp hack to make sure we select the best candidate from a set of 10 audio samples
				size.do { |index|

					snd = p[index+1]; // to avoid retrieving the same sound of the query

					//check if snd.id already exists, if so, take next sound
					if (metadata.size > 0,
						{
							while ( {this.sndidexist(snd.id) == 1},
								{
									index = index + 1 + size;
									snd = p[index];
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


}

