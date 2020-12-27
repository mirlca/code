/////////////////////
/////////////////////
/////////////////////

// 1) CREATION OF THE DATASET
// This process needs to be done twice: one for training and the other for testing.

/////////////////////
/////////////////////
/////////////////////


// * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *

(
Freesound.authType = "token"; // default, only needed if you changed it
Freesound.token="<your_api_key>"; // change it to own API key token, otherwise it will not work.
)

// * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *



// ************************************
// File read
// ************************************


// Store in an array the text file with ID and labels info for a set of sounds

/////////////////////

// Change the url and name of the file accordingly e.g "training.txt" or "testing.txt"
// The examples can be found in "datasets/training-input.txt" and "datasets/testing-input.txt"
(
x = List.new;
x = CSVFileReader.read("/Desktop/training.txt", skipEmptyLines: true) // change to the correct filename and directory
)
x.size

/////////////////////

// Reminder: Data structure input data: ID label

// The ID is in the position 0 of the array: x[0][n] => ID of the sound in the first line

// The label is in the position 1 of the array: x[n][0] => Label of the sound located in the first line
// The size of the array: x.size

// ************************************
// Get and store the values of n descriptors for each sound (by ID) using Freesound quark
// ************************************

// We need to create a Freesound object for each ID, then get the values of multiple descriptors using different methods, and then store this information in the existing array

/////////////////////


(
d = Dictionary.new;
c = Condition.new;
e = Condition.new;
y = List.new;

fork {
	x.do({ arg item, i;
		10.wait; // This is the amount of time in sec. For example, 10 sec means 6 sounds per minute, therefore  100 sounds will take around 16 min
		c.test = false;
		e.test = false;

		FSSound.getSound(item[0], {|f|
			y = List.new;
			c.wait;
			~snd = f;
			"sound is downloaded".postln;
			y.add(item[1]); // label
			~snd.getAnalysis( "lowlevel.mfcc", {|val|
				y.add(val.lowlevel.mfcc.mean[0]);
				y.add(val.lowlevel.mfcc.mean[1]);
				y.add(val.lowlevel.mfcc.mean[2]);
				y.add(val.lowlevel.mfcc.mean[3]);
				y.add(val.lowlevel.mfcc.mean[4]);
				y.add(val.lowlevel.mfcc.mean[5]);
				y.add(val.lowlevel.mfcc.mean[6]);
				y.add(val.lowlevel.mfcc.mean[7]);
				y.add(val.lowlevel.mfcc.mean[8]);
				y.add(val.lowlevel.mfcc.mean[9]);
				y.add(val.lowlevel.mfcc.mean[10]);
				y.add(val.lowlevel.mfcc.mean[11]);
				y.add(val.lowlevel.mfcc.mean[12]);
				y.add(val["lowlevel"]["mfcc"]["var"][0]);
				y.add(val["lowlevel"]["mfcc"]["var"][1]);
				y.add(val["lowlevel"]["mfcc"]["var"][2]);
				y.add(val["lowlevel"]["mfcc"]["var"][3]);
				y.add(val["lowlevel"]["mfcc"]["var"][4]);
				y.add(val["lowlevel"]["mfcc"]["var"][5]);
				y.add(val["lowlevel"]["mfcc"]["var"][6]);
				y.add(val["lowlevel"]["mfcc"]["var"][7]);
				y.add(val["lowlevel"]["mfcc"]["var"][8]);
				y.add(val["lowlevel"]["mfcc"]["var"][9]);
				y.add(val["lowlevel"]["mfcc"]["var"][10]);
				y.add(val["lowlevel"]["mfcc"]["var"][11]);
				y.add(val["lowlevel"]["mfcc"]["var"][12]);
		}, true);
			d.add(item[0].asInteger -> y); // dictionary key = ID; dictionary value = y array
			e.wait;
			"sound descriptors are downloaded".postln;
		}); // getsound
		c.test = true;
		c.signal;
		e.test = true;
		e.signal;
	}); // do
	"Done".postln;
}; // fork

)

/////////////////////

d.size
d.postln

/////////////////////

// We store the info as a text file for our records

// Change the url and name of the file accordingly e.g **training-output-complete** or **testing-output-complete**
// The examples can be found in "datasets/training-input-complete.txt" and "datasets/testing-input-complete.txt"

// File write
(
f = File("/Desktop/training-output-complete.txt", "w"); // change to the correct filename and directory
f.write("id,label,mfcc_m[0],mfcc_m[1],mfcc_m[2],mfcc_m[3],mfcc_m[4],mfcc_m[5],mfcc_m[6],mfcc_m[7],mfcc_m[8],mfcc_m[9],mfcc_m[10],mfcc_m[11],mfcc_m[12],mfcc_v[0],mfcc_v[1],mfcc_v[2],mfcc_v[3],mfcc_v[4],mfcc_v[5],mfcc_v[6],mfcc_v[7],mfcc_v[8],mfcc_v[9],mfcc_v[10],mfcc_v[11],mfcc_v[12]"++"\n");
d.keysValuesDo { |key, value|
	// postln("the key: " ++ key ++ " the value: " ++ value);
	f.write(key.asString++","++value[0].asString++","++value[1].asString++","++value[2].asString++","++value[3].asString++","++value[4].asString++","++value[5].asString++","++value[6].asString++","++value[7].asString++","++value[8].asString++","++value[9].asString++","++value[10].asString++","++value[11].asString++","++value[12].asString++","++value[13].asString++value[0].asString++value[1].asString++value[2].asString++value[3].asString++value[4].asString++value[5].asString++value[6].asString++value[7].asString++value[8].asString++value[9].asString++value[10].asString++value[11].asString++value[12].asString++"\n");
};
f.close();
)


/////////////////////

// SAVING THE DICTIONARY TO HARDISK (this is what we will use for the NN)

////////////////////

q = (); // Event for the testing file

q[\testing_dataset] = d;

Archive.global.put(\myTestingDataDDMMYYYY, q);

Archive.global.at(\myTestingDataDDMMYYYY).postcs;


////////////////////

r = (); // Event for the training file

r[\training_dataset] = d;

Archive.global.put(\myTrainingDataDDMMYYYY, r);

Archive.global.at(\myTrainingDataDDMMYYYY).postcs;


/////////////////////
/////////////////////
/////////////////////

// 2) LOADING THE TRAINING AND TESTING DATASETS FOR TRAINING
// This is executed once both training and testing data have been created and saved to hardisk

/////////////////////
/////////////////////
/////////////////////


// after recompiling:
s.boot;

r = Archive.global.at(\myTrainingDataDDMMYYYY);
r.postcs;
r[\training_dataset].value;
r[\training_dataset].size


q = Archive.global.at(\myTestingDataDDMMYYYY);
q.postcs;
q[\testing_dataset].value;
q[\testing_dataset].size


/////////////////////


// We need to create 2 dictionaries for each original, one for labels and one for descriptors:
// Create 4 new dictionaries (2 data and 2 labels)
// Create 2 FluidDatasets and 2 FluidLabelSet


(
~training_dict = Dictionary();
~training_label_dict = Dictionary();
~test_dict = Dictionary();
~test_label_dict = Dictionary();
)

// Parse dictionaries and create datasets as expected by FluidDataSet

(
r[\training_dataset].keysDo {|key|
	~result = r[\training_dataset].at(key);
	~training_dict.add(key -> ~result[1..26].asFloat); // number of inputs
	~training_label_dict.add(key -> ~result[0].asSymbol.asArray);
}
)
~training_dict
~training_label_dict

/////////////////////

(
q[\testing_dataset].keysDo {|key|
	~result = q[\testing_dataset].at(key);
	~test_dict.add(key -> ~result[1..26].asFloat); // number of inputs
	~test_label_dict.add(key -> ~result[0].asSymbol.asArray);
}
)

/////////////////////

(
~source_dataset.free;
d = Dictionary.new;
d.add(\cols -> 26); // number of inputs
d.add(\data -> ~training_dict);
fork{
    ~source_dataset = FluidDataSet(s,\mlpclassify_sourcedata); s.sync;
    ~source_dataset.load(d); s.sync;
	~source_dataset.dump; s.sync;
}
)

/////////////////////

(
~training_label_dataset.free;
d = Dictionary.new;
d.add(\cols -> 1);
d.add(\data -> ~training_label_dict);
fork{
    ~training_label_dataset = FluidLabelSet(s,\mlpclassify_labels); s.sync;
    ~training_label_dataset.load(d); s.sync;
	~training_label_dataset.dump; s.sync;
}
)

/////////////////////

(
~test_dataset.free;
d = Dictionary.new;
d.add(\cols -> 26); // number of inputs
d.add(\data -> ~test_dict);
fork{
    ~test_dataset = FluidDataSet(s,\mlpclassify_testdata); s.sync;
    ~test_dataset.load(d); s.sync;
	~test_dataset.dump; s.sync;
}
)

/////////////////////

(
~test_label_dataset.free;
d = Dictionary.new;
d.add(\cols -> 1);
d.add(\data -> ~test_label_dict);

fork{
    ~test_label_dataset = FluidLabelSet(s,\mlpclassify_testlabels); s.sync;
    ~test_label_dataset.load(d); s.sync;
	~test_label_dataset.dump; s.sync;
}
)


/////////////////////


// NORMALAZING

// FluidStandardize

~standardizer = FluidStandardize(s);

~stand_dataset.free
~stand_test_dataset.free
~stand_dataset = FluidDataSet(s,\mlpclassify_standdata);
~stand_test_dataset = FluidDataSet(s,\mlpclassify_stand_test_data);

(
~standardizer.fitTransform(~source_dataset, ~stand_dataset, {
"Done".postln;
});
)

~standardizer.transform(~test_dataset, ~stand_test_dataset);
~source_dataset.cols
~source_dataset.size
~source_dataset.print
~stand_dataset.print
~stand_test_dataset.print


// FluidPCA - note that you can overwrite existing datasets

~pca = FluidPCA(s,20);
(
~pca.fitTransform(~stand_dataset, ~stand_dataset, {
"Done".postln;
});
)
(
~pca.transform(~stand_test_dataset, ~stand_test_dataset, {
"Done".postln;
});
)

~source_dataset.print
~stand_dataset.print
~stand_test_dataset.print


~classifier.free;
~classifier = FluidMLPClassifier(s, [14], FluidMLPClassifier.relu, 10000, 0.01, 0.9, 10, 0.2);

//////////////////////

//Fit the classifier to the example DataSet and labels, and then run prediction on the test data into our mapping label set
~classifier.fit(~stand_dataset,~training_label_dataset,action:{|loss| ("Trained"+loss).postln});

FluidMLPClassifier

/////////////////////


~test_predicted_label_dataset.free
~test_predicted_label_dataset = FluidLabelSet(s,\mlpclassify_predictlabels);

//Run the test data through the network, into the predicted labelset

~classifier.predict(~stand_test_dataset, ~test_predicted_label_dataset, action:{"Test complete".postln});


~test_predicted_label_dataset.dump


~test_predicted_label_dataset.write("/Desktop/predicted.txt");  // change to the correct filename and directory

~test_label_dataset.write("/Desktop/groundtruth.txt");  // change to the correct filename and directory


/////////////////////


// Accuracy

(
fork{
~num_valid = 0;
~test_label_dict.keysValuesDo{|k,v|
~test_predicted_label_dataset.getLabel(k, {|l|
if(l==v[0].asString){
~num_valid = ~num_valid+1};
});
s.sync;
};
(~num_valid/~test_label_dict.size).postln;
}
)

///////////////////////////////
/// SAVING MODEL
///////////////////////////////


~classifier.write("/Desktop/MIRLC/model.JSON") // change to the correct filename and directory

~standardizer.write("/Desktop/MIRLC/standardizer.JSON") // change to the correct filename and directory

~pca.write("/Desktop/MIRLC/pca.JSON") // change to the correct filename and directory
