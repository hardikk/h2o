options(echo=FALSE)
##
# Utilities for relative paths in R
##

SEARCHPATH <- NULL
calcPath<-
function(path, root) {
    if (basename(path) == "h2o" || "smalldata" %in% dir(path)) {
        print("[WARN]: Could not find the bucket that you specified! Checking R/*.")
        SEARCHPATH <<- path
        return(-1)
    }
    if (basename(path) == root || root %in% dir(path)) return(0)
    if (basename(dirname(path)) == root || root %in% dir(dirname(path))) return(1)
    return(ifelse( calcPath( dirname( path), root) < 0, -1, 1 + calcPath( dirname( path), root) ) )
}

genDots<-
function(distance) {
    if(distance == 0) return('./')
    return(paste(rep("../", distance), collapse=""))
}

locate<-
function(dataName = NULL, bucket = NULL, path = NULL, fullPath = NULL, schema = "put") {
    if (!is.null(fullPath)) {   
        if (schema == "local") return(paste("./", gsub("[./]",fullPath), sep = ""))
        return(fullPath) #schema is put
    }

    if(!is.null(bucket)) {
        if(is.null(path)) stop("\"path\" must be specified along with bucket. Path is the bucket offset.")
        bucket <- gsub("[./]","",bucket)
        cat("ALTERED BUCKET: ", bucket)
        path   <- ifelse(substring(path,1,1) == '/', substring(path,2), path)
        path   <- ifelse(substring(path,nchar(path)) == '/', substring(path,1,nchar(path)-1),path)
        if (schema == "local") return(paste("./",bucket,"/",path,sep = ""))
        if (schema == "put") {
            distance.bucket.root <- calcPath(getwd(), bucket)
            if (distance.bucket.root < 0) {
                Log.err(paste("Could not find bucket ", bucket, "\n"))
            }
            cat("\n IS MY BUCKET HERE?? :", bucket)
            bucket.dots <- genDots(distance.bucket.root)
            fullPath <- paste(bucket.dots,bucket,'/',path,sep="")
            print("PATH BEING USED: ")
            print(fullPath)
            return(fullPath)
        }
        if (schema == "S3") stop("Unimpl")
    }

    if (!is.null(dataName)) {
        bn <- basename(dataName)
        dataName <- dirname(dataName)
        dataName <- gsub("\\.","", gsub("\\./","",dataName))
        if(!is.null(SEARCHPATH)) return(paste(SEARCHPATH, "/", dataName, "/", bn, sep = ""))
        psplit <- strsplit(dataName, "/")[[1]]
        bucket <- psplit[1]
        path   <- paste(psplit[-1], bn, collapse="/", sep = "/")
        print("FETCHING BUCKET AND DATA")
        cat("BUCKET: ", bucket, " PATH: ", path, " SCHEMA: ", schema) 
        return(locate(bucket = bucket, path = path, schema = schema))
    }
}

getBucket<-
function(bucket = NULL) {
    if(is.null(bucket)) stop("Did not specify bucket...")
    print(bucket)
    bucket <- gsub("[./]","",bucket)
    distance.bucket.root <- calcPath(getwd(), bucket)
    bucket.dots <- genDots(distance.bucket.root)
    newBucket <- paste(bucket.dots, bucket, sep  ="")
    return(newBucket)
}

distance <- calcPath(getwd(), "tests")
if (distance < 0) {
    path <- paste(SEARCHPATH, "/R/", sep = "")
    source(paste(path, "tests/Utils/h2oR.R", sep = ""))
    source(paste(path, "h2oRClient-package/R/Algorithms.R", sep = ""))
    source(paste(path, "h2oRClient-package/R/Classes.R", sep = ""))
    source(paste(path, "h2oRClient-package/R/ParseImport.R", sep = ""))
    source(paste(path, "h2oRClient-package/R/Internal.R", sep = ""))
    sandbox()
} else {
    distance <- calcPath(getwd(), "tests")
    dots     <- genDots(distance)
    newPath  <- paste(dots, "Utils/h2oR.R", sep = "")
    source(newPath)

    #rdots is the calculated path to the R source files...
    rdots <- ifelse(dots == "./", "../", paste("../", dots, sep = ""))

    source(paste(rdots, "h2oRClient-package/R/Algorithms.R", sep = ""))
    source(paste(rdots, "h2oRClient-package/R/Classes.R", sep = ""))
    source(paste(rdots, "h2oRClient-package/R/ParseImport.R", sep = ""))
    source(paste(rdots, "h2oRClient-package/R/Internal.R", sep = ""))
    sandbox()
}
