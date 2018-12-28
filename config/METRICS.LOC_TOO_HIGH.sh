
TEMP_DIR=$(dirname $0)/../tmp

metricName="lc"

# *******************************************************************
# DO NOT CHANGE
# *******************************************************************

strFunctionMetrics="$1"
metricThreshold="$2"

oldIFS=$IFS
	
IFS=,	read -a arrayline<<<$(echo "$strFunctionMetrics")
	
IFS=$oldIFS

fileName="${arrayline[0]}"
funcName="${arrayline[1]}"
metricsMeasure=
funcLinePos=

for i in "${arrayline[@]}"; do
	
	if [[ "$i" =~ ^"$metricName":[0-9]+ ]]; then
		sep=$(expr index "$i" : ) ; 
    name=${i:0:$sep-1} ;
    metricsMeasure=${i:$sep} ;
		#echo "$name = $metricsMeasure"  > /dev/tty
	elif [[ "$i" =~ ^ml:[0-9]+ ]]; then
		sep=$(expr index "$i" : ) ; 
    name=${i:0:$sep-1} ;
    funcLinePos=${i:$sep} ;
		#echo "$name = $funcLinePos"  > /dev/tty
	fi

done

if [ -z "$funcLinePos" ]; then
	echo "Unable to get line position of function $funcName in $strFunctionMetrics" >> "$TEMP_DIR"/$$-$(basename $0 .sh)-error.txt 
	exit -1
fi

if [ -z "$metricsMeasure" ]; then
	echo "Unable to get measure for metrics $metricName of function $funcName in $strFunctionMetrics" >> "$TEMP_DIR"/$$-$(basename $0 .sh)-error.txt
	exit -1
fi

if [[ ( "$metricThreshold" != "-1" ) && ( $metricsMeasure -ge $metricThreshold ) ]]; then


	# *******************************************************************
	# You can use your own defect description in the JSON code below
	# *******************************************************************

	cat <<EOF
	{
	    "checker" : "METRICS.LOC_TOO_HIGH",
	    "extra" : "metric_loc_violation",
	    "file" : "$fileName",
	    "function" : "$funcName",
	    "subcategory" : "code_quality",
	    
	    "properties" : {
	        "type" : "Function with high number of lines of code are harder to maintain",
	        "category" : "Code maintainability issues",
	        "impact" : "low",
	        "longDescription" : "The number of line of code in $funcName is $metricsMeasure which is above the threshold $metricThreshold.",
	        "localEffect" : "Hard to maintain function",
	        "issueKind" : "QUALITY"
	    },    
	    "events" : [{
	        "tag" : "LOC metric violation",
	        "file": "$fileName",
	        "description" : "The value of the code quality metric $metricName is $metricsMeasure which is above the threshold $metricThreshold.",
	        "line" : $funcLinePos,
	        "main" : true
	        }
	    ] 
	}

EOF
fi