
// Before instantiating MIRLCa, it is recommended that you define what path you will be storing the generated JSON files.
// The slash at the end is very important.
// Please note: the directory should exist!
// Please note for MIRLCa to work in general: the three files "model.JSON", "pca.JSON" and "standardizer.JSON" should exist inside this folder (see MIRLCa-intro-performance.scd for further details)

p = "/Users/anna/Desktop/MIRLCa/"

File.mkdir(p);      // This line will create the directory if it doesn't exist yet.

// MIRLCa instantiation

a = MIRLCa.new(path: p)

// You can either train by ID or by random or by a combined approach of random and by ID


///////////////////////////////////////

// To start training by random

a.starttraining // random by default

// Or

a.starttraining("random")

// If you start training by random, you need to wait until the sound is dowloaded, and then you need to execute the methods 'ok' or 'ko' depending on whether you like the sound or not. Once you annotate the sound, a new sound will be downloaded for you to annotate. This will happen in cycle until you stop the training.

// If you like the sound, execute this command

a.ok

// If you don't like the sound, execute this command

a.ko

///////////////////////////////////////////

// To start training by ID

a.starttraining("id")

// If you start training by ID, you need to give details of what sound you want to annotate. When the sound is dowloaded, you need to execute the methods 'ok' or 'ko' depending on whether you like the sound or not. Once you annotate the sound, you will need to tell what's the next sound you want to annotate until you stop training.


a.trainid(3333); // ok or ko?

// If you like the sound, execute this command

a.ok

// If you don't like the sound, execute this command

a.ko


a.trainid(3334); // ok or ko?
a.trainid(3335); // ok or ko?
a.trainid(3336); // ok or ko?
a.trainid(3337); // ok or ko?
a.trainid(3338); // ok or ko?
a.trainid(3332); // ok or ko?
a.trainid(3339); // ok or ko?
a.trainid(4334); // ok or ko?
a.trainid(4335); // ok or ko?
a.trainid(4336); // ok or ko?
a.trainid(4337); // ok or ko?
a.trainid(4338); // ok or ko?
a.trainid(4339); // ok or ko?


// You can either pause the process or stop training. Pause should be executed when a new sound has been downloaded, otherwise you might need to execute this command twice.

a.pause

// A new sound will appear, sometimes you need to wait. Keep saying if you like the sound or not.

// Once you are done, you can stop the training. Stop training should be executed when a new sound has been downloaded. Otherwise you might need to execute "Pause" to stop playing the latest sound.

a.stoptraining

// If you don't like the result, you can continue training with the same mode

a.continuetraining

// You can continue the training by changing the method from id to random, or the other way around

a.starttraining("id")

// or

a.starttraining("random")


// After stop training, if you are happy with the accuracy results, execute this command to save your results to JSON files.

a.save
