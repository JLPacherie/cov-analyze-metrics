
declare _defectTagList=( \
	CHECKER EXTRA FILE FUNCTION SUBCATEGORY TYPE CATEGORY IMPACT CWE \
	LONG_DESCRIPTION LOCAL_EFFECT ISSUE_KIND TAG DESCRIPTION URL \
	LINK_TEXT LINE MAIN \
)

defectTemplate='
{
    "checker" : CHECKER,
    "extra" : EXTRA,
    "file" : ISSUE_FILE,
    "function" : FUNCTION,
    "subcategory" : SUBCATEGORY,
    
    "properties" : {
        "type" : TYPE,
        "category" : CATEGORY,
        "impact" : IMPACT,
        "cwe" : CWE,
        "longDescription" : LONG_DESCRIPTION,
        "localEffect" : LOCAL_EFFECT,
        "issueKind" : ISSUE_KIND
    },    
    "events" : [{
        "tag" : TAG,
        "file": EVENT_FILE,
        "description" : EVENT_DESCRIPTION,
        "linkUrl" : URL,
        "linkText" : LINK_TEXT,  
        "line" : LINE,
        "main" : MAIN
        }
    ] }
'

# -----------------------------------------------------------------------------

# Remove all undefined tags in the defect template.
function cleanupDefectTemplate() {
	local defectText="$1"
	for tag in ${_defectTagList[@]}; do
		#DEBUG echo "Removing $tag" > /dev/tty
		# Removing "tag" : TAG
		defectText=$(echo $defectText | sed "s/\"[a-Z]*\"[ ]*:[ ]*$tag[ ,]*//g" )

		
		#DEBUG echo "$defectText" > /dev/tty
	done

	defectText=$(echo $defectText | sed "s/,[ ]*}/}/g" )

	echo $defectText
}

#DEBUG echo $(cleanupDefectTemplate "$defectTemplate")


# -----------------------------------------------------------------------------

# getFormatedDefect tag1 value1 tag2 value2 ../..

function getFormatedDefect() {
	

	#debug echo "fileName='$defectFileName'" > /dev/tty 
  local defectText="$defectTemplate"
 	#echo "$defectText" > /dev/tty 
 	while `test $# -gt 0`; do
     #debug echo "Replacing $1 with $2"
	   defectText=$(echo "$defectText" | sed "s#$1#$2#g" )
     shift ; shift
 	done

  echo $(cleanupDefectTemplate "$defectText")

}

# -----------------------------------------------------------------------------
# Extract the list of file to analyse
# -----------------------------------------------------------------------------

function getCoverityMetrics() {

	local idir="$1"
	local output="$2"

	file="$1"/output/FUNCTION.metrics.xml.gz

	if [ -f "$file" ]; then

		# The XML metrics file is missing a root element for XSLT processing
		echo "<root>" > $TEMP_DIR/$$-rawmetrics.xml
		zcat $file >> $TEMP_DIR/$$-rawmetrics.xml
		echo "</root>" >> $TEMP_DIR/$$-rawmetrics.xml

		xsltproc bin/func-metrics.xsl $TEMP_DIR/$$-rawmetrics.xml | \
		  sed 's/,[ ]*fn:/,/g' | \
		  sed 's/,[ ]*/,/g'    | \
		  sed 's/;,/,/g'       | \
		  sed 's/;/,/g'       | \
		  sort -t, -k 1 > $TEMP_DIR/$$-rawmetrics.csv

		if [ "$TEMP_DIR/$$-rawmetrics.csv" != "$output" ]; then
			cp "$TEMP_DIR/$$-rawmetrics.csv" "$output"
		fi

	else
		echo "Unable to find $file" > /dev/tty
		exit -1
	fi
}

