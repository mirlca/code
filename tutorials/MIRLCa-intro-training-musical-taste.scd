
// Before instantiating MIRLCa, it is recommended that you define what path you will be storing the generated JSON files.
// The slash at the end is very important.
// Please note: the directory should exist!
// Please note for MIRLCa to work in general: the three files "model.JSON", "pca.JSON" and "standardizer.JSON" should exist inside this folder (see MIRLCa-intro-performance.scd for further details)

p = "/Users/anna/Desktop/MIRLCa/"

File.mkdir(p);      // This line will create the directory if it doesn't exist yet.

// MIRLCa instantiation

a = MIRLCa.new(path: p)

// Start the training

a.starttraining

// If you like the sound, execute this command

a.ok

// If you don't like the sound, execute this command

a.ko

// You can either pause the process or stop training. Pause should be executed when a new sound has been downloaded, otherwise you might need to execute this command twice.

a.pause

// A new sound will appear, sometimes you need to wait. Keep saying if you like the sound or not.

// Once you are done, you can stop the training. Stop training should be executed when a new sound has been downloaded. Otherwise you might need to execute "Pause" to stop playing the latest sound.

a.stoptraining

// If you don't like the result, you can continue training

a.continuetraining

// After stop training, if you are happy with the accuracy results, execute this command to save your results to JSON files.

a.save
