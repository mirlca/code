// @new users, control the following customizable vars:
// - Freesound.token = "<your_api_key>"
// - path: replace current directory to your own directory to store downloaded sounds and record the text file with the credits, or change it to "Platform.defaultTempDir" or "/tmp/"
// - debugging: True/False
// For sound credits, you can pass an absolute path including the filename as an argument e.g. "/Users/anna/Desktop/credits/Freesound_credits.txt", otherwise it will generate a text file for each instance.
// e.g.
// p = "/Users/anna/Desktop/MIRLC/"; c ="/Users/anna/Desktop/credits/Freesound_credits.txt";
// a = MIRLCRep2.new(path: p, creditspath: c)


MIRLCRep2 {

    classvar <>server;
    classvar <>file;
    classvar <>date;
    classvar <>debugging;
    var metadata, buffers, synths, effects, <translation;
    var snd, preview, buf, target, sequential, granular;
    var poolsizeold, index, keys, counter, sndid, numsnds, size, rndnums;
    var window, viewoscil;
    var backendClass;
    var databaseSize;
    var directoryPath;
	var g0, g1;
	var b0, b1, b2;
	var fxlpf;
	var effectson;
    var playing;
	var creditsfilename;
	var creditsfilepath;

	var maxvol =0.2;// 0.07; // 0.2; // audio samples

	*new {|backend = 0, dbSize = 478456, path = (Platform.defaultTempDir), creditsPath = (Platform.defaultTempDir) |
        ^super.new.init(backend, dbSize, path, creditsPath)
    }

    init {|backend, dbSize, path, creditsPath|
        server = Server.local;
        server.boot;
        metadata = Dictionary.new;
        buffers = Dictionary.new;
        synths = Dictionary.new;
		effects = Dictionary.new;
        translation = Dictionary.new;
        debugging = False; // True
        poolsizeold = 0;
        counter = 0;
        sequential = False;
		granular = False;
        databaseSize = dbSize;
        directoryPath = path;
		effectson = 0;
        playing = True;
		creditsfilepath = creditsPath;
		date = Date.getDate;

        if(backend == 0){
			try {
				backendClass = FSSound;
			} // end try
			{|error| [\catchFreesoundClass, error].postln };
			try {
            Freesound.authType = "token"; // default, only needed if you changed it
            Freesound.token="<your_api_key>"; // change it to own API key token
			} // end try
			{|error| [\catchFreesoundToken, error].postln };
        }{
			try {
				backendClass = FLSound;
			} //end try
			{|error| [\catchFlucomaSound, error].postln };
        };

		// Management of credits file
		try {
			if (creditsfilepath != nil, {
				creditsfilename = creditsfilepath.standardizePath ++ date.stamp ++ "_credits" ++ ".txt";
			}, {

			});
		} // end try
		{|error| [\catchFileName, error].postln };
		try {
			file = File(creditsfilename,"a");
			file.write("Sound samples used from Freesound.org:\n");
			file.close;
		} // end try
		{|error| [\catchFileWrite, error].postln };
		// end of management of credits file

       this.argstranslate;

		postln("INFO MIRLC: Sounds will be downloaded at: " ++ directoryPath);
		postln("INFO MIRLC: A sound credits list will be created at: " ++ creditsfilepath);

		server.waitForBoot {

			g0 = Group.new(server);
			g1 = Group.after(g0);
			b0 =  Bus.audio(server);
			//b1 =  Bus.audio(server);
			//b2 =  Bus.audio(server);

		SynthDef(\synth_mono_fs, {
            |bufnum, buf, amp = 1, out = 0, rate = 1, da = 0, loop = 1, trig = 0, gate = 1, atk=0.1, sus=1, rel=1.0|
            var sig, env;
            sig = PlayBuf.ar(1, bufnum, BufRateScale.kr(buf) * rate,  doneAction: da, loop: loop, trigger: trig);
			env = EnvGen.ar(Env.asr(atk,sus,rel), gate, doneAction:2);
			sig = sig * amp * env;
			Out.ar(out, sig!2);
        }).add;


		// Filters

		SynthDef(\lowpass_filter, {
			arg ibs, obs, amp=1,
			atk=0.1, sus=1, rel=0.2, crv=2, gate=1;
			var source, sig, env;
			env = EnvGen.ar(Env.asr(atk, sus, rel, crv), gate);
			source = In.ar([ibs, ibs]);
			sig = LPF.ar(source);
			Out.ar(obs, sig*env*amp);
		}).add;

		SynthDef(\highpass_filter, {
			arg ibs, obs, amp=1,
			atk=0.1, sus=1, rel=0.2, crv=2, gate=1;
			var source, sig, env;
			env = EnvGen.ar(Env.asr(atk, sus, rel, crv), gate);
			source = In.ar([ibs, ibs]);
			sig = HPF.ar(source);
			Out.ar(obs, sig*env*amp);
		}).add;

		SynthDef(\bandpass_filter, {
			arg ibs, obs, amp=1,
			atk=0.1, sus=1, rel=0.2, crv=2, gate=1, freq;
			var source, sig, env;
			env = EnvGen.ar(Env.asr(atk, sus, rel, crv), gate);
			source = In.ar([ibs, ibs]);
			sig = BPF.ar(source, freq); //%TODO
			Out.ar(obs, sig*env*amp);
		}).add;

		SynthDef(\bitcrush, { // The Decimator effect is an "Extension". TODO: Find a standalone solution.
			arg ibs, obs, amp=0.4,
			atk=0.02, sus=1, rel=0.1, crv=2, gate=1,
			rate=44100, bit=8;
			var source, sig, env;
			env = EnvGen.ar(Env.asr(atk, sus, rel, crv), gate);
			source = In.ar([ibs, ibs]);
			sig = Decimator.ar(source, rate, bit);
			Out.ar(obs, sig*env*amp);
		}).add;

		SynthDef(\rverb, {
			arg ibs, obs, amp=1,
			atk=0.02, sus=1, rel=0.1, crv=2, gate=1,
			mix = 0.5 /*dry/wet balance 0..1*/,
			rm = 0.5 /*room size 0..1*/,
			damp=0/*reverb HF damp 0..1*/;
			var source, sig, env;
			env = EnvGen.ar(Env.asr(atk, sus, rel, crv), gate);
			source = In.ar([ibs, ibs]);
			sig = FreeVerb.ar(source, mix, rm, damp);
			Out.ar(obs, sig*env*amp);
		}).add;

		SynthDef.new(\dlay, {
			arg ibs=0, obs=0, amp=0.8, deltime = 0.3, mix = (-0.5), decay=3, delHz = 0.25, delMin = 0.1, delMax = 0.4;
			var sig, delay;
			sig = In.ar([ibs, ibs]);
			delay = CombL.ar(sig, 0.5, SinOsc.kr([delHz, delHz*0.9]).exprange(delMin, delMax), decay);
			sig = XFade2.ar(sig, delay, mix) * amp;
			Out.ar(obs, sig);
		}).add;

			SynthDef(\distort, { // The CrossoverDistortion is an "Extension". TODO: Find a standalone solution.
			arg ibs, obs, amp=1,
			atk=0.1, sus=1, rel=0.2, crv=2, gate=1, ampfx=0.01, smooth=0.01;
			var source, sig, env;
			env = EnvGen.ar(Env.asr(atk, sus, rel, crv), gate);
			source = In.ar([ibs, ibs]);
			sig = CrossoverDistortion.ar(source, ampfx, smooth);
			Out.ar(obs, sig*env*amp);
		}).add;

		SynthDef(\vibrato, {
			arg ibs, obs, amp=1,
			atk=0.1, sus=1, rel=0.2, crv=2, gate=1, maxdelaytime=0.01;
			var source, sig, env;
			env = EnvGen.ar(Env.asr(atk, sus, rel, crv), gate);
			source = In.ar([ibs, ibs]);
			// sig = DelayC.ar(source, maxdelaytime, delaytime);
			sig = DelayC.ar(source, maxdelaytime, SinOsc.ar(Rand(5,10),0,0.0025,0.0075));
			Out.ar(obs, sig*env*amp);
		}).add;


		SynthDef(\compress, {
			arg ibs, obs, amp=1,
			atk=0.1, sus=1, rel=0.2, crv=2, gate=1, gain=1.5, threshold=0.5;
			var source, sig, env;
			env = EnvGen.ar(Env.asr(atk, sus, rel, crv), gate);
			source = In.ar([ibs, ibs]);
			// sig = DelayC.ar(source, maxdelaytime, delaytime);
			sig = CompanderD.ar(gain*source, threshold, 1, 0.5);
			Out.ar(obs, sig*env*amp);
		}).add;


		/*SynthDef(\flanger, {
			arg ibs, obs, amp=1,
			atk=0.1, sus=1, rel=0.2, crv=2, gate=1, maxdelaytime=0.02, flangefreq=0.1, fdback=0.1;
			var source, sig, env;
			env = EnvGen.ar(Env.asr(atk, sus, rel, crv), gate);
			source = In.ar([ibs, ibs]);
			sig = DelayN.ar(source, maxdelaytime, SinOsc.kr(flangefreq,0,0.005,0.005));

			Out.ar(obs, sig*env*amp);
		}).add;*/


/*		SynthDef(\limit, {
			arg ibs, obs, amp=1,
			atk=0.1, sus=1, rel=0.2, crv=2, gate=1, gain=1;
			var source, sig, env;
			env = EnvGen.ar(Env.asr(atk, sus, rel, crv), gate);
			source = In.ar([ibs, ibs]);
			// sig = DelayC.ar(source, maxdelaytime, delaytime);
			sig = Limiter.ar(gain*source, 0.99, 0.01);
			Out.ar(obs, sig*env*amp);
		}).add;*/


       //this.scope;

		};




    } //--//


    //---------------------------------------------------//
    //SOUND GROUP MANAGEMENT (PRIVATE FUNCTIONS)
    //---------------------------------------------------//
    // FUNCTIONS: load, loadmetadata


    //------------------//
    // RETRIEVE SOUNDS
    //------------------//
    // This private function manages the dictionary metadata (sounds with fs info) and loads the new sounds
    // This function is in charge of the storage of a new group of sounds by managing the right index number when calling the function load() for each new sound from a dictionary of json info and resetting the counter.
    loadmetadata { |totalsnds = 1|
        totalsnds.do ({ |index|
            this.loadsounds(metadata, (index + poolsizeold) );
        });
        poolsizeold = metadata.size;
        counter = 0; // used in random
        this.printmetadata;
    }

    //------------------//
    // LOAD SOUNDS
    //------------------//
    // This private function parses the Freesound information of each sound and converts it to the SuperCollider language, storing all the info in two dictionaries (buffers and Synths). The result is a sound that plays once is correctly stored in the synths dictionary.
    loadsounds { |dict, index|
        dict[index].retrievePreview(directoryPath, {
			/*("previewFilename").postln;
			dict[index].previewFilename.postln;*/
            buf = Buffer.readChannel(server, directoryPath ++ dict[index].previewFilename,
                channels: [0],
                action: { |buf|
                    if (sequential == False,
                        {
							if (effectson == 0,
								{
									synths.add (index -> Synth.new(\synth_mono_fs, [\buf, buf, \bufnum, buf.bufnum, \loop, 1, \amp, maxvol], g0) ); },
								{
										synths.add (index -> Synth.new(\synth_mono_fs, [\buf, buf, \bufnum, buf.bufnum, \loop, 1, \amp, maxvol, \out, b0]) );
								});

                        },
                        {
                            // do nothing if in sequential mode
                    });
                    buffers.add(index -> buf);
            });
        });
    } //--//

    //---------------------------------------------------//
    //QUERIES TO SEED A POOL OF SOUNDS (TEXT, CONTENT)
    //---------------------------------------------------//
    // FUNCTIONS: random, tag, content

    //------------------//
    // GET SOUND BY ID
    //------------------//
    // This public and private function can be used as a standalone public function to get [1..n] sounds by ID, and it is also used as a private function by random, tag, similar, filter, content to get sounds
    // params: id, size
    id { |id = 31362, size = 1|

		// If (size > 1 && sound exists in the folder) // in the future store the metadata as well and so then if (size > 1)
		// just copy the info from previous
		// else:

        backendClass.getSound( id,
            { | f |
                //available metadata: "id","url","name","tags","description","geotag","created","license","type","channels","filesize""bitrate","bitdepth","duration","samplerate","username","Jovica","pack","pack_name","download","bookmark","previews","preview-lq-mp3","preview-hq-ogg","preview-hq-mp3","images","num_downloads","avg_rating","num_ratings","rate":,"comments","num_comments","comment","similar_sounds","analysis","analysis_frames","analysis_stats"
				// ways of printing info:
				// "name".postln; or snd["name"].postln;
				// this.printmetadata;

				// snd = f;
				var tmpSnd = f;

				if ( tmpSnd.isNil, {
					postln("ERROR: There was a problem downloading the sound with ID " ++ id ++ "\nINFO: Please try again.");},
				{

					if ( tmpSnd["detail"]=="Not found.", {
						postln("ERROR: Sound details not found.\nINFO: Please try another sound.");
					}, {

					if ( debugging == True,{"ERROR: Sound exists in the database.".postln;});

					index = metadata.size;

					metadata.add(index -> f);

					if ( size == 1, {
						this.loadmetadata(size);
					},{ // size > 1
						if ( (metadata.size - poolsizeold) == size, // need to wait until asynchronous call is ready! once all sounds are added in the dictionary, they can be retrieved
						{
                            this.loadmetadata(size);
                        }
                    );
					}); // end if

					try {
						file.open(creditsfilename,"a");
						file.write(tmpSnd["name"] ++ " by " ++ tmpSnd["username"] ++ " (" ++ tmpSnd["url"] ++") licensed under " ++ tmpSnd["license"] + "\n");
						file.close();
						} //end try
					{ |error| [\catchFileWrite, error].postln }; // end catch error

					}); // End IF (tmpSnd["detail"]=="Not found." vs sound found

				}); // End IF tmpSnd.isNil
		}); // End backendClass.getSound

    } //--//

    //------------------//
    // QUERY BY RANDOM
    //------------------//
    // This public function gets [1..n] sounds by random, and plays them
	// Previously was checking whether the sound existed with snd["detail"] != nil but it seems to work better snd["detail"]=="Not found."
    random { |size = 1|

		var sndid;

        if ( debugging == True, {postln("INFO: Number of sounds selected by random: " ++ size);} );

        sndid = rrand (1, databaseSize);

        backendClass.getSound ( sndid,
            { |f|

				// snd = f;
				var tmpSnd = f;

				if ( tmpSnd.isNil, {
					postln("ERROR: There was a problem downloading the random sound with ID " ++ sndid ++ "\nINFO: Please try again.");},
				{

                if ( tmpSnd["detail"]=="Not found.",
                    {
						if ( debugging == True, {"ERROR: SoundID does not exist".postln;} );
						"WARNING: Sound not found in the database. Getting another sound.".postln;
                        this.random(size);
                    },
                    {
                        if ( debugging == True, {
                            postln("INFO: Potential sound candidate: ");
                            tmpSnd["name"].postln;
							postln("Counter value is: " + counter);
                        });

                        counter = counter + 1; // adding one sound to the counter in a recursive fashion
                        if (size == 1,
                            {
                                this.id(sndid, size);
                            },
                            {//size > 1
								if ( debugging == True, {
									this.id(sndid, size);
									postln("INFO: Group size is greater than 1");
									postln("INFO: ( counter - size ): " ++ ( counter - size ));
									}); // end if debugging

                                if ( counter <= size ,
                                    {
                                        this.id(sndid, size);
                                        if ( counter < size, { this.random(size); } );
									}
                                );
                            }
                        );
					});
				});	// End IF tmpSnd.isNil
		}); // End Function backendClass.getSound
    } //--//

    //------------------//
    // QUERY BY TAG
    //------------------//
    // This public function gets [1..n] sounds by one defined tag, and plays them
    tag { |tag = "noise", size = 1|

        if ( debugging == True, {
            postln("Sounds selected by tag: " ++ size);
        });

        backendClass.textSearch(query: tag, params: ('page': 1),
            action: { |p|
				if ( p.isNil,
				{
					postln("ERROR: There was a problem downloading this sound using the tag method.\nINFO: Please try again.");
				}, {

						size.do { |index|
							snd = p[index];
							postln("INFO: Found sound by tag, id: " ++ snd["id"] ++ "name: " ++ snd["name"]);
							while (
								{this.sndidexist(snd.id) == 1},
								{
									postln ("INFO: Repeated sound, getting another sound...");
									index = index + 1 + size;
									snd = p[index];
							});
							this.id(snd.id, 1); // so that each sound is loaded & directly played
						}
				}); // End IF p.isNil
		    });	// End Function backendClass.textSearch

    } //--//


    //------------------//
    // QUERY BY CONTENT
    //------------------//
    // This public function gets [1..n] sounds by one defined feature and fx, and plays them
    content { |size = 1, feature = 'dur', fvalue = 1, fx = 'conf', fxvalue = 'bypass' |
        var fconcat, fxconcat;
        if (feature != 'id',
          { fconcat = this.gettranslation(feature.asSymbol)++fvalue; },
          { fconcat = fvalue });
        fxconcat = this.gettranslation(fx.asSymbol) ++ this.gettranslation(fxvalue);

        backendClass.contentSearch(
            target: fconcat,
            filter: fxconcat,
            params: ('page':1),
            action: {|p|
				if ( p.isNil,
				{
					postln("ERROR: There was a problem downloading this sound using the content method.\nINFO: Please try again.");
				}, {

					size.do { |index|
						snd = p[index];
						//check if snd.id already exists, if so, take next sound
						if (metadata.size > 0,
							{
								while ( {this.sndidexist(snd.id) == 1},
									{
										index = index + size;
										snd = p[index];
										postln ("repeated sound, getting another sound...");
								});
						});
						this.id(snd.id, 1);  // so that each sound is loaded directly played;
					} // End size.do
				}); // End of p.isNil
            }); // End Function backenClass.contentSearch
    } //--//

    //------------------//
    // QUERY BY PITCH
    //------------------//
    // This public function gets [1..n] sounds by pitch
	pitch {|size = 1, fvalue = 440, fx = 'conf', fxvalue = 'lo'|

		this.content(size, 'pitch', fvalue, fx, fxvalue);

	}//-//

    //------------------//
    // QUERY BY BPM
    //------------------//
    // This public function gets [1..n] sounds by BPM
	bpm {|size = 1, fvalue = 60, fx = 'conf', fxvalue = 'lo'|

		this.content(size, 'bpm', fvalue, fx, fxvalue);

	} //-//

    //------------------//
    // QUERY BY DURATION
    //------------------//
    // This public function gets [1..n] sounds by duration
	dur {|size = 1, fvalue = 10, fx = 'conf', fxvalue = 'lo'|

		this.content(size, 'dur', fvalue, fx, fxvalue);

	} //-//

    //------------------//
    // QUERY BY DISSONANCE
    //------------------//
    // This public function gets [1..n] sounds by dissonance
	diss {|size = 1, fvalue = 1.0, fx = 'conf', fxvalue = 'lo'|

		this.content(size, 'dissonance', fvalue, fx, fxvalue);

	} //-//


    //---------------------------------------------------//
    // QUERIES TO CONTINUE ADDING SOUNDS (QUERY BY EXAMPLE)
    //---------------------------------------------------//
    // FUNCTIONS: similar, filter

    //------------------//
    // SIMILAR SOUNDS
    //------------------//
    // This public function gets [1..n] similar sounds from a target sound, usually the first sound from the dictionary
    similar { | targetnumsnd = 0, size = 1 |

        target = metadata[targetnumsnd];  // before: metadata[targetnumsnd - 1];

        target.getSimilar(
            action: { |p|

			if ( p.isNil,
			{
				postln("ERROR: There was a problem downloading this sound using the similar method.\nINFO: Please try again.");
			}, {
                size.do { |index|
                    snd = p[index+1]; // to avoid retrieving the same sound of the query
                    //check if snd.id already exists, if so, take next sound
                    if (metadata.size > 0,
                        {
                            while ( {this.sndidexist(snd.id) == 1},
                                {
                                    index = index + 1 + size;
                                    snd = p[index];
                                    postln ("INFO: repeated sound, getting another sound...");
                            });
                    });
                    this.id(snd.id, 1); // so that each sound is loaded directly played
                } // End Size do
			}); // End IF p.isNil
        }); // End Function target.getSimilar


    } //--//

    //------------------//
    // SIMILAR AUTO
    //------------------//
    // This public function gets [1..n] similar sounds from a target sound, scheduled to be downloaded on every certain interval.
	similarauto { |targetnumsnd = 0, size = 3, tempo = 30|

		var counter = size;
		var offset = targetnumsnd;
		var t = TempoClock.new;

		t.sched(tempo, {
			//"hello".postln; // still 3
			//n.postln;
			"INFO: getting a similar sound (auto mode)...".postln;
			this.similar(offset);
			counter = counter - 1;
			offset = offset + 1;
			if (counter <= 0,
				{nil},
				{tempo}
			);
		});

	}	//--//

    //------------------//
    // SAME ID AUTO
    //------------------//
    // This public function downloads and plays the same sound a number of times on every certain interval.

		sameidauto { |id = 0, size = 3, tempo = 30|

		var counter = size;
		var t = TempoClock.new;

		t.sched(tempo, {
			"INFO: getting same sound (auto mode)...".postln;
			this.id(id);
			counter = counter - 1;
			if (counter <= 0,
				{nil},
				{tempo}
			);
		});

	}	//--//

    //------------------//
    // SIMILAR BY RANGE
    //------------------//
    // This public function gets [1..n] similar sounds from a target sound filtered by a fx
    filter { |targetnumsnd = 0, size = 1, fx = 'conf', fxvalue = 'bypass' |

        var  fxconcat;
        fxconcat = this.gettranslation(fx.asSymbol) ++ this.gettranslation(fxvalue);

        sndid = metadata[targetnumsnd].id; // before: metadata[targetnumsnd - 1].id

        backendClass.contentSearch(
            target: sndid,
            filter: fxconcat,
            params: ('page':1),
            action: { |p|

				if ( p.isNil,
				{
					postln("ERROR: There was a problem downloading this sound using the filter method.\nINFO: Please try again.");
				}, {

					size.do { |index|
						snd = p[index];
						//snd.name.postln;
						//check if snd.id already exists, if so, take next sound
						if (metadata.size > 0,
							{
								while ( {this.sndidexist(snd.id) == 1},
									{
										index = index + size;
										snd = p[index];
										postln ("repeated sound, getting another sound...");
								});
						});
						this.id(snd.id, 1); // so that each sound is loaded directly played
					} // End size.do

				});	// End IF p.isNil

            }); // End Function backendClass.contentSearch
    } //--//

    //---------------------------------------------------//
    // ANALYZING SOUNDS / AIDING METHODS
    //---------------------------------------------------//
    // FUNCTIONS:

    //------------------//
    // ANALYZE
    //------------------//
    // This public function retrieves all content-based descriptors listed in the Analysis Descriptor Documentation from the FreeSound API: "https://www.freesound.org/docs/api/analysis_docs.html#analysis-docs"
    // The result can be filtered using the descriptors request parameter passing a list of comma separated descriptor names chosen from the available descriptors e.g. 'descriptors=lowlevel.mfcc,rhythm.bpm'
    analyze {|descriptors, action|

        metadata.size.do( { |index|
            metadata[index].getAnalysis( descriptors, action, {|val|
                val.postln;
            }, true)
        });

    }//--//

	 //------------------//
    // WHAT ID
    //------------------//
	// Public function
    whatid { |feature = "id" |
        metadata.size.do ({ |index|
            postln("[" ++ index ++ "]: " ++ "id: " ++ metadata[index].id);
        });
    }//--//

	 //------------------//
    // WHAT MASTER VOLUME
    //------------------//
	// Public function
    whatvol {
		postln("[" ++ maxvol ++ "]");
    }//--//


    //------------------//
    // WHAT PITCH
    //------------------//
	// Public function
    whatpitch { |feature = "lowlevel.pitch.mean" |
        this.analyze(feature);
    }//--//

    //------------------//
    // WHAT KEY
    //------------------//
	// Public function
    whatkey { |feature = "tonal.key_key" |
        this.analyze(feature);
    }//--//

    //------------------//
    // WHAT BPM
    //------------------//
	// Public function
    whatbpm { |feature = "rhythm.bpm" |
        this.analyze(feature);
    }//--//

    //------------------//
    // WHAT DURATION (sec)
    //------------------//
	// Public function
    whatdur { |feature = "sfx.duration" |
        this.analyze(feature);
    }//--//


    //---------------------------------------------------//
    // FUNCTIONS FOR LIVE CODING WITH THE SOUNDS
    //---------------------------------------------------//
    // FUNCTIONS: play, sequence, sequencemachine (private), parallel, stop, solo, solo all, mute, mute all, free, free all

    //------------------//
    // PLAY
    //------------------//
    // This public function plays the sounds of the same group at the same rate
    play {|rate = 1|
        size = synths.size;
		"THE SIZE OF SYNTHS IS "+synths.size;
		//if( granular == True, { granular = False });
        size.do( { |index|
			if( effectson == 0,
				{
					synths[index].set(\amp, maxvol, \rate, rate, \out, 0);
				},
				{
					synths[index].set(\amp, maxvol, \rate, rate, \out, b0);
				}
			);

            this.printsynth(index);
        });
    } //--//

	// Need to deactivate it calling at playauto(0)

    //------------------//
    // PLAY AUTO
    //------------------//
    // This public function plays the sounds of the same group several times at different rates that are changed on every certain interval
	playauto { |times = 4, tempo = 30|

		var counter = times;
		var bool = 1;
		var speed = 1;

		var t = TempoClock.new;

		t.sched(tempo, {

			if (counter >= 0 && playing == True,
				{
					if ( bool == 1,
						{
						speed = 1.0.rand.postln*(-1);
						this.play(speed);
						"Play backwards <<".postln;
						bool = 0;
						},
						{
						speed = 1.0.rand.postln;
						this.play(speed);
						"Play forwards >>".postln;
						bool = 1;
						}
					);
					counter = counter - 1;
					tempo;
				},
				{
                    if ( playing == True, {
                        "Done playauto".postln;
                        nil;
                        this.play(1);
                    }, {
                        "Stopped playauto".postln;
                        nil;
                    });

				}
			);
		});

	}	//--//

    //------------------//
    // PLAY AUTODOWN
    //------------------//
    // This public function plays the sounds of the same group several times at different rates that increasingly slow down that are changed on every certain interval
	playautodown { |startspeed = 1, endspeed = 0, times = 5, tempo = 10|

		var period = abs(endspeed - startspeed) / times;//0.2
		var counter = startspeed - period; //1
		var speed = startspeed - period;

		var t = TempoClock.new;

		t.sched(tempo, {

			if (counter >= 0.01 && playing == True,
				{
					this.play(speed);
					"playing at: "+speed.postln;
					counter = counter - period;
					speed = speed - period;
					tempo;
				},
				{
                    if ( playing == True, {
                        "Done playdown".postln;
                        nil;
                        this.stop;
                    },
                    {
                       "Done playdown".postln;
                       nil;
                    }
                    );
				}
			);
		});

	}	//--//

   //------------------//
    // AUTOCHOPPED
    //------------------//
    // This public function plays the sounds of the same group several times at randomly assigned speeds during a certain interval

	autochopped { |times = 4, tempo = 1|

		//loop times
		//this.playauto(RAND)

		var counter = times;
		var bool = 1;
		var speed = 1;

		var t = TempoClock.new;

		//tempo = tempo*[1,2,3,4,5].choose

		t.sched(tempo, {
			"tempo: "+tempo.postln;
			if (counter >= 0 && playing == True,
				{
					if ( bool == 1,
						{
						speed = 1.0.rand.postln*(-1);
						this.play(speed);
						"Play backwards <<".postln;
						bool = 0;
						},
						{
						speed = 1.0.rand.postln;
						this.play(speed);
						"Play forwards >>".postln;
						bool = 1;
						}
					);
					counter = counter - 1;
					tempo;
				},
				{
                    if (playing == True, {
                        "Done playauto";
                        nil;
                        this.play(1);
                    }, {
                        "Done playauto";
                        nil;
                    });



				}
			);
		});

	} //--//


	//------------------//
    // BYPASS
    //------------------//
    // This public function bypasses the effects
	bypass {
		effectson = 0;
		this.play;
	}//--//

	//------------------//
    // LPF
    //------------------//
    // This public function applies a lowpass filter.
    lowpf {

		effectson = 1;
        size = synths.size;
        size.do( { |index|
            synths[index].set(\out, b0);
            this.printsynth(index);
        });

		if ( effects.size>0,
			{
				effects[0].free;
			}
		);

		effects.add (0 -> Synth.new(\lowpass_filter, [\obs, 0, \ibs, b0], g1));


    } //--//

	//------------------//
    // HPF
    //------------------//
    // This public function applies a highpass filter.
    highpf {

		effectson = 1;
        size = synths.size;
        size.do( { |index|
            synths[index].set(\out, b0);
            this.printsynth(index);
        });

		if ( effects.size>0,
			{
				effects[0].free;
			}
		);

        effects.add (0 -> Synth.new(\highpass_filter, [\obs, 0, \ibs, b0], g1));

    } //--//

	//------------------//
    // BPF
    //------------------//
    // This public function applies a bandpass filter.
   bandpf { |freq = 440|

		effectson = 1;
        size = synths.size;
        size.do( { |index|
            synths[index].set(\out, b0);
            this.printsynth(index);
        });

		if ( effects.size>0,
			{
				effects[0].free;
			}
		);

        effects.add (0 -> Synth.new(\bandpass_filter, [\freq, freq, \obs, 0, \ibs, b0], g1));

    } //--//

	//------------------//
    // BitCrush
    //------------------//
    // This public function applies a bitcrush filter.
    bitcrush {

		effectson = 1;
        size = synths.size;
        size.do( { |index|
            synths[index].set(\out, b0);
            this.printsynth(index);
        });

		if ( effects.size>0,
			{
				effects[0].free;
			}
		);

		effects.add (0 -> Synth.new(\bitcrush, [\obs, 0, \ibs, b0], g1));

    } //--//


	//------------------//
    // Reverb
    //------------------//
    // This public function applies a reverb.
    reverb {

		effectson = 1;
        size = synths.size;
        size.do( { |index|
            synths[index].set(\out, b0);
            this.printsynth(index);
        });

		if ( effects.size>0,
			{
				effects[0].free;
			}
		);
		effects.add (0 -> Synth.new(\rverb, [\obs, 0, \ibs, b0], g1));



    } //--//


	//------------------//
    // Delay
    //------------------//
    // This public function applies a delay.
    delay {

		effectson = 1;
        size = synths.size;
        size.do( { |index|
            synths[index].set(\out, b0);
            this.printsynth(index);
        });

		if ( effects.size>0,
			{
				effects[0].free;
			}
		);

		effects.add (0 -> Synth.new(\dlay, [\obs, 0, \ibs, b0], g1));
    } //--//


	//------------------//
    // Distortion
    //------------------//
    // This public function applies a distortion
    distort { | ampfx = 0.1 |

		effectson = 1;
        size = synths.size;
        size.do( { |index|
            synths[index].set(\out, b0);
            this.printsynth(index);
        });

		if ( effects.size>0,
			{
				effects[0].free;
			}
		);

		effects.add (0 -> Synth.new(\distort, [\obs, 0, \ibs, b0], g1));
    } //--//


	//------------------//
    // Vibrato
    //------------------//
    // This public function applies a phaser.
    vibrato { | maxdelaytime = 0.01 |

		effectson = 1;
        size = synths.size;
        size.do( { |index|
            synths[index].set(\out, b0);
            this.printsynth(index);
        });

		if ( effects.size>0,
			{
				effects[0].free;
			}
		);

		effects.add (0 -> Synth.new(\vibrato, [\obs, 0, \ibs, b0], g1));
    } //--//

	//------------------//
    // Flanger
    //------------------//
    // This function applies a phaser
    /*flanger { | maxdelaytime = 0.2 |

		effectson = 1;
        size = synths.size;
        size.do( { |index|
            synths[index].set(\out, b0);
            this.printsynth(index);
        });

		if ( effects.size>0,
			{
				effects[0].free;
			}
		);

		effects.add (0 -> Synth.new(\flanger, [\obs, 0, \ibs, b0], g1));
    }*/ //--//

	//------------------//
    // Compression
    //------------------//
    // This function applies a compressor.
    compress { | threshold = 0.5 |

		effectson = 1;
        size = synths.size;
        size.do( { |index|
            synths[index].set(\out, b0);
            this.printsynth(index);
        });

		if ( effects.size>0,
			{
				effects[0].free;
			}
		);

		effects.add (0 -> Synth.new(\compress, [\obs, 0, \ibs, b0], g1));
    } //--//


	//------------------//
    // Limiter
    //------------------//
    // This function applies a limiter
   /* limit { | gain = 1 |

		effectson = 1;
        size = synths.size;
        size.do( { |index|
            synths[index].set(\out, b0);
            this.printsynth(index);
        });

		if ( effects.size>0,
			{
				effects[0].free;
			}
		);

		effects.add (0 -> Synth.new(\limit, [\obs, 0, \ibs, b0], g1));
    } *///--//


    //------------------//
    // SEQUENCE
    //------------------//
    // This public function plays sounds sequentially, one after the other
    sequence {
        "INFO: Sequence mode".postln;
		if ( sequential == False,
			{
				"INFO: STATE 3: from parallel to sequence".postln; sequential = True;
				sequential.postln;
				"INFO: change behavior from PARALLEL to SEQUENCE".postln;
				// removing all the sounds except for the first
				 index = 0;
                synths.size.do{ |b|
                    if( b>0,
                        {
                            this.free(b);
                        }
                    );
                };
                synths[index].set(\loop, 0, \da, 2, \amp, maxvol);
                this.printsynth(index);
                synths[index].onFree {
					if ( sequential == True, {
						"--- sequencemachine mode".postln;
						this.sequencemachine(index);
					});
                };
				//
		}, {
				"INFO: STATE 4: from sequence to sequence".postln; sequential.postln;
				//sequential = True;
				"INFO: keep behavior SEQUENCE".postln;
		});
    } //--//

    //------------------//
    // SEQUENCE MACHINE (PRIVATE)
    //------------------//
    // This function is private and makes sure to play sounds sequentially
    sequencemachine { |index = 0|

		if ( (index+1) < buffers.size, {index = index + 1}, {index = 0} );
		"INFO: index value in sequence machine: ".postln;
		index.postln;
		if(effectson==0,
			{
				synths.add (index -> Synth.new(\synth_mono_fs, [\buf, buffers[index], \bufnum, buffers[index].bufnum, \loop, 0, \da, 2, \amp, maxvol], g0) );
			},
			{
				synths.add (index -> Synth.new(\synth_mono_fs, [\buf, buffers[index], \bufnum, buffers[index].bufnum, \loop, 0, \da, 2, \amp, maxvol, \out, b0]) );
			}
		);

        this.printsynth(index);
        synths[index].onFree {
            if (sequential == True,
            {
			"inside recursion".postln;
            this.sequencemachine(index);
            } );
        };
    } //--//

    //------------------//
    // PARALLEL
    //------------------//
    // This public function plays sounds in parallel, all of them looping at the same time. If it comes from sequential, it will start once the sound that is playing in the sequential state ends.
    parallel {
        "INFO: Parallel mode".postln;

			if ( sequential == True,
			{
				"INFO: STATE 1: from sequence to parallel".postln;
				sequential = False;
				sequential.postln;
				"INFO: change behavior from SEQUENCE to PARALLEL".postln;
				this.parallelmachine;
				/*if ( synths != nil, {
					"change behavior from SEQUENCE to PARALLEL".postln;
						this.parallelmachine;
					}, {
					"not deleting".postln;
					"change behavior from SEQUENCE to PARALLEL".postln;
					this.parallelmachine;
				});*/

		}, {
				"INFO: STATE 2: from parallel to parallel".postln; sequential.postln;
				//sequential == False;
				"INFO: keep behavior PARALLEL". postln;
		});


    }

	//------------------//
    // PARALLEL MACHINE (PRIVATE)
    //------------------//
    // This is a private function that makes sure to play sounds in parallel
    parallelmachine {

        size = buffers.size;
        size.do( { |index|
			if( effectson==0,
				{
					synths.add (index -> Synth.new(\synth_mono_fs, [\buf, buffers[index], \bufnum, buffers[index].bufnum, \loop, 1, \da, 0, \amp, maxvol], g0) );
				},
				{
					synths.add (index -> Synth.new(\synth_mono_fs, [\buf, buffers[index], \bufnum, buffers[index].bufnum, \loop, 1, \da, 0, \amp, maxvol, \out, b0]) );
				}
			);
        });
        this.printsynths;
		this.play;

    } //--//



    //------------------//
    // VOLUME
    //------------------//
    // This public function sets the volume of the group of sounds 0..1
    volume {|vol = 0.2|
        size = synths.size;
        size.do( { |index|
            synths[index].set(\amp, vol);
        });
    } //--//

    //------------------//
    // STOP
    //------------------//
	// This public function stops the sound of the group of sounds (sets the amplitude to zero)
    stop {
        size = synths.size;
        size.do( { |index|
            synths[index].set(\amp, 0);
        });
    } //--//

    //------------------//
    // FADE OUT
    //------------------//
	// This public function fades out all synths of a group of sounds with a smooth fade out

    fadeout {|release = 1.0|
        playing = False;
		sequential = False; // to avoid inconsistencies

		if ( debugging == True,{
			postln("Number of sounds fading out: " ++ synths.size);
		});


		synths.size.do( { |index|
			try {
				synths[index].set(\gate, 0, \rel, release, \da, 2);
			} // try
			{|error| [\catchFadeout, error].postln };
		});


    } //--//


    //------------------//
    // SOLO
    //------------------//
    // This public function mutes all the sounds except for the selected sound from a given group
	  solo { |targetnumsnd = 0|
        synths.size.do( { |index|
            if (index == (targetnumsnd), // before: (index == (targetnumsnd-1)
                {synths[index].set(\amp, maxvol)},
                {synths[index].set(\amp, 0)}
            );
        });
    } //--//

    //------------------//
    // MUTE
    //------------------//
    // This public function mutes a selected sound from a given group
    mute { |targetnumsnd = 0|
        synths[targetnumsnd].set(\amp, 0); // before: synths[targetnumsnd-1].set(\amp, 0);
    } //--//

    //------------------//
    // MUTE ALL
    //------------------//
    // This public function mutes all the sounds from a given group
    muteall {
        synths.size.do( { |index|
            synths[index].set(\amp, 0);
        });
    } //--//

	//------------------//
	// FREE ALL
	//------------------//
	// This is a private function that frees all the synths from a given group
	freeall {
		synths.size.do( { |index|
			synths[index].free;
		});
	} //--//

	//------------------//
	// FREE
	//------------------//
	// This is a private function that frees a given synth from a given group
    free {|index|
        synths[index].free;
    } //--//

    //---------------------------------------------------//
    // UTILS
    //---------------------------------------------------//
    // FUNCTIONS: sndexist, argstranslate, cmdperiod

    //------------------//
    // DOES A SOUND EXIST
    //------------------//
    // This private function returns whether the sound is already in the metadata dictionary or not
    sndidexist { |id|
        var index;
        var mdsize = metadata.size;

        block( { |break|
            mdsize.do( { |index|
                //postln(metadata[0].id == id);
                //postln(index);
                if ( metadata[index].id == id,
                    { ^1 }
                );
            });
            {^0};
        });
    } //--//

    //------------------//
    // TRANSLATE TO FS ARGS
    //------------------//
    // This private function maps from shorter arguments to the ones expected by the FreeSound quark
    argstranslate {
        //Features
        translation.add(\pitch -> ".lowlevel.pitch.mean:");
        translation.add(\dur -> ".sfx.duration:");
        translation.add(\dissonance -> ".lowlevel.dissonance.mean:");
        translation.add(\bpm -> ".rhythm.bpm:");
        //Filters
        translation.add(\key -> "tonal.key_key:");
        translation.add(\scale -> "tonal.key_scale:");
        translation.add(\conf -> ".lowlevel.pitch_instantaneous_confidence.mean:");
        translation.add(\mfcc0 -> "lowlevel.mfcc.mean[0]:");
        translation.add(\mfcc1 -> "lowlevel.mfcc.mean[1]:");
        translation.add(\mfcc4 -> "lowlevel.mfcc.mean[4]:");
        //Filter values
        translation.add(\Asharp-> "\"ASharp\"");
        translation.add(\A-> "\"A\"");
        translation.add(\B-> "\"B\"");
        translation.add(\C-> "\"C\"");
        translation.add(\D-> "\"D\"");
        translation.add(\E-> "\"E\"");
        translation.add(\F-> "\"F\"");
        translation.add(\G-> "\"G\"");
        translation.add(\major -> "\"major\"".asString);
        translation.add(\minor -> "\"minor\"".asString);
        translation.add(\hi -> "[0.8 TO 1]");
        translation.add(\lo -> "[0 TO 0.2]");
        translation.add(\bypass -> "[0 TO 1]");
        translation.add(\1720 -> "[17 TO 20]");
        translation.add(\2040 -> "[20 TO 40]");
        translation.add(\neg -> "[-1124 TO -1121]");
        //translation.add(\diss -> "lowlevel.dissonance.mean:"); // 0 -> consonant, 1-> dissonant [0.3-0.5]
        //translation.add(\voiced -> "lowlevel.spectral_entropy.mean:"); // [2-10]

    } //--//


    //------------------//
    // GET TRANSLATION
    //------------------//
    // This private function translates a parameter only if it exists in the dictionary translation

    gettranslation{|key|
    		if (translation.includesKey(key)){
    			^translation[key];
    		}{
    			^key;
    		}

    	}

    //------------------//
    // CMD PERIOD
    //------------------//
    // This public function is activated when stopping the code / recompiling / etc.
    cmdPeriod {
        currentEnvironment.clear;
    } //--//


    //---------------------------------------------------//
    // VISUALIZATION, PRINTING
    //---------------------------------------------------//
    // FUNCTIONS: scope, plotserver, printmedata, printsynths, printbuffers, printall

    //------------------//
    // SCOPE
    //------------------//
    // This public function plots a waveform scope

    scope {
        /*window = Window.new("MIRLC scope", Rect(200, 200, 1200, 500));
        window.view.decorator = FlowLayout(window.view.bounds);
        viewoscil = Stethoscope.new(server, view:window.view);
        window.onClose = { viewoscil.free }; // don't forget this
        window.front;*/
        Stethoscope.new(server);
    } //--//

    //------------------//
    // PLOT SERVER
    //------------------//
    // This public function plots the server
    plotserver {
		//server.scope;
		server.plotTree;
		server.queryAllNodes;
    } //--//

    //------------------//
    // PRINT METADATA
    //------------------//
    // This function prints the FS metadata information for all downloaded sounds
    printmetadata {
        metadata.size.do ({ |index|
            postln("[" ++ index ++ "]: " ++ "id: " ++ metadata[index].id ++ " name: " ++ metadata[index].name ++ " by: " ++ metadata[index].username ++ " dur: " ++ metadata[index].duration);
        });
    } //--//

	// DUPLICATED from "printmetadata". TODO: merge it
    //------------------//
    // PRINT METADATA
    //------------------//
    // This function prints the FS metadata information for all downloaded sounds
    info {
        metadata.size.do ({ |index|
            postln("[" ++ index ++ "]: " ++ "id: " ++ metadata[index].id ++ " name: " ++ metadata[index].name ++ " by: " ++ metadata[index].username ++ " dur: " ++ metadata[index].duration);
        });
    } //--//

    //------------------//
    // PRINT BUFFERS
    //------------------//
    // This public function prints the buffers information and associated FS metadata information for all downloaded sounds
    printbuffers {
        buffers.size.do ({ |index|
            postln("[" ++ index ++ "]: " ++ buffers[index] ++ "id: " ++ metadata[index].id ++ " name: " ++ metadata[index].name ++ " by: " ++ metadata[index].username);
        });
    } //--//

    //------------------//
    // PRINT SYNTHS
    //------------------//
    // This public function prints the synths information and associated FS metadata information for all the active sounds
    printsynths {
        synths.size.do ({ |index|
            //postln("[" ++ index ++ "]: " ++ synths[index] ++ "id: " ++ metadata[index].id ++ " name: " ++ metadata[index].name ++ " by: " ++ metadata[index].username );
            postln("now playing..." ++ "[" ++ index ++ "]: " ++ "id: " ++ metadata[index].id ++ " name: " ++ metadata[index].name ++ " by: " ++ metadata[index].username ++ " dur: " ++ metadata[index].duration ++ $\n ++ synths[index] );
        });
    } //--//

    //------------------//
    // PRINT SYNTH
    //------------------//
    // This public function prints the synth information and associated FS metadata information of the current active sound
    printsynth { |index|
        postln("now playing..." ++ "[" ++ index ++ "]: " ++ "id: " ++ metadata[index].id ++ " name: " ++ metadata[index].name ++ " by: " ++ metadata[index].username ++ " dur: " ++ metadata[index].duration ++ $\n ++ synths[index] );
    } //--//

    //------------------//
    // PRINT ALL (METADATA, BUFFERS, SYNTHS)
    //------------------//
    // This public function prints the 3 arrays stored during a session.
    printall {
        postln("FS metadata dictionary: ");
        this.printmetadata;
        postln("buffers dictionary: ");
        this.printbuffers;
        postln("synths dictionary: ");
        this.printsynths;
    } //--//

    //------------------//
    // CREDITS
    //------------------//
    // This public function is activated when stopping the code / recompiling / etc. It prints the list of sounds used in a session.
    credits {
		var listcredits;
		try {
			file.open(creditsfilename,"r");
			listcredits = file.readAllString;
			file.close();
			postln("********************************");
			listcredits.postln;
			postln("********************************");
		} //end try
		{|error| [\catchFileWrite, error].postln }; // end catch error
    } //--//

}



