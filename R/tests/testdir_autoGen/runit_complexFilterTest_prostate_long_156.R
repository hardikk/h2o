            ##
            # Author: Autogenerated on 2013-11-27 18:13:59
            # gitHash: c4ad841105ba82f4a3979e4cf1ae7e20a5905e59
            # SEED: 4663640625336856642
            ##
            source('./findNSourceUtils.R')
            Log.info("======================== Begin Test ===========================")
            complexFilterTest_prostate_long_156 <- function(conn) {
                Log.info("A munge-task R unit test on data <prostate_long> testing the functional unit <['', '==']> ")
                Log.info("Uploading prostate_long")
                hex <- h2o.uploadFile(conn, locate("../../smalldata/logreg/prostate_long.csv.gz"), "rprostate_long.hex")
            Log.info("Performing compound task ( ( hex[,c(\"DPROS\")] == 3.45710935045 ))  on dataset <prostate_long>")
                     filterHex <- hex[( ( hex[,c("DPROS")] == 3.45710935045 )) ,]
            Log.info("Performing compound task ( ( hex[,c(\"ID\")] == 139.782195581 ))  on dataset prostate_long, and also subsetting columns.")
                     filterHex <- hex[( ( hex[,c("ID")] == 139.782195581 )) , c("GLEASON","DPROS","PSA","DCAPS","VOL","CAPSULE","RACE","ID","AGE")]
                Log.info("Now do the same filter & subset, but select complement of columns.")
                     filterHex <- hex[( ( hex[,c("ID")] == 139.782195581 )) , c(1)]
            }
            conn = new("H2OClient", ip=myIP, port=myPort)
            tryCatch(test_that("compoundFilterTest_ on data prostate_long", complexFilterTest_prostate_long_156(conn)), warning = function(w) WARN(w), error = function(e) FAIL(e))
            PASS()
