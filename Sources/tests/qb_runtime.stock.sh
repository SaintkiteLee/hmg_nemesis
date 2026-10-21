#!/system/bin/sh

SELECT_FUNC=$1
EXTRA_ARG=$2
THIS_FILE="qb_runtime.sh"
RED_LOG="\x1b[1;31m"
COLOR_CLEAR="\x1b[0m"
LOG_DEV="/dev/kmsg"
#LOG_DEV="/dev/qb_com"

if [ $# != 1 ] ; then
	if [ $# != 2 ] ; then
		(echo "Select Func! - Stop." &> $LOG_DEV)
		return
		exit 0
	fi
fi

function e2fsck_log() {
	#	CMD : e2fsck_log
	(echo "======== e2fsck_log ========================================" &> $LOG_DEV)
	
	(echo "Start to e2fsck_log func!" &> $LOG_DEV)
	(echo "Start e2fsck - Cache Partition!" &> $LOG_DEV)
	(/system/bin/e2fsck -p /dev/block/platform/bdm/by-name/cache &> $LOG_DEV)

	(echo "Start e2fsck - Userdata Partition!" &> $LOG_DEV)
	(/system/bin/e2fsck -p /dev/block/platform/bdm/by-name/userdata &> $LOG_DEV)

	(echo "End of e2fsck_log func!" &> $LOG_DEV)
}

function attached_logs() {
	#	CMD : attached_logs
	local CHK_PTN=$1

	if [ $# != 1 ] ; then
		(echo "Input additional data : Checking PARTITION." &> $LOG_DEV)
		(echo "	Ex) $THIS_FILE attached_logs /data" &> $LOG_DEV)
		exit 0
		return
	fi
	(echo "======== attached_logs - $CHK_PTN ==========================" &> $LOG_DEV)
	(echo "======== lsof List - $CHK_PTN ==============================" &> $LOG_DEV)
	(/system/bin/lsof | /system/bin/grep $CHK_PTN &> $LOG_DEV)

	FUSER_LOG=$(/system/bin/busybox fuser -m $CHK_PTN)

	(echo "" &> $LOG_DEV)
	(echo "======== Show fuser processes - $CHK_PTN ===================" &> $LOG_DEV)
	(echo "FUSER LOG : $FUSER_LOG" &> $LOG_DEV)

	TAIL_STR=$FUSER_LOG
	while [ "true" ]
	do
		UID_STR=${TAIL_STR%% *}
		TAIL_STR=${TAIL_STR#* }
		UID_LEN=${#UID_STR}

		if [ "$UID_LEN" -eq "0" ] ; then
			break
		fi
		
		(/system/bin/ps "$UID_STR" | /system/bin/grep "$UID_STR" &> $LOG_DEV)
	done

	(echo "" &> $LOG_DEV)
	(echo "======== Mount List =======================================" &> $LOG_DEV)
	(/system/bin/mount &> $LOG_DEV)

	(echo "" &> $LOG_DEV)
}

function unmount() {
	#	CMD : unmount
	local UM_PTN=$1
	
	if [ $# != 1 ] ; then
		(echo "Input additional data : un-mount PARTITION." &> $LOG_DEV)
		(echo "	Ex) $THIS_FILE unmount /data" &> $LOG_DEV)
		exit 0
		return
	fi
	
	(echo "Start to un-mount func!" &> $LOG_DEV)
	(/system/bin/umount $UM_PTN &> $LOG_DEV)

	RETVAL=$?

	(echo "qb_runtime unmount return ( $RETVAL ) " &> $LOG_DEV)

	if [ "$RETVAL" -eq "0" ]; then
		(echo ">>sh test unmount ok " &> $LOG_DEV)
		exit 0
	else
		(echo ">>sh test unmount fail " &> $LOG_DEV)
		(/system/bin/mount &> $LOG_DEV)		
		exit 1
       fi
	
	(echo "End of un-mount func!" &> $LOG_DEV)
}


check_fixed_uid()
{

	PKG_LIST_FILE="/data/system/packages.list"
	PKG_ACTIVE_UID="10200"

	ERROR_LOG="false"

	local NOW_LINE=""

	#################### Check if UID List file is exist or not ####################
	if test -e $PKG_LIST_FILE
	then
		echo "The Package UID List file is exist." > $LOG_DEV
	else
		echo "The Package UID List file is not exist. Stop checking UID..." > $LOG_DEV
		return
		exit 0
	fi

	while read ONE_LINE
	do
		NOW_LINE=$ONE_LINE

		local STR1=${NOW_LINE%% *}
		local STR1_R=${NOW_LINE#* }
		local STR2=${STR1_R%% *}
		local STR2_R=${STR1_R#* }

		PKG_NAME=$STR1
		PKG_UID=$STR2

		if [ "$PKG_UID" -ge "$PKG_ACTIVE_UID" ] ; then
			echo $RED_LOG"$PKG_NAME : $PKG_UID is not Fixed UID!!"$COLOR_CLEAR > $LOG_DEV
			ERROR_LOG="true"
		fi
	done < $PKG_LIST_FILE

	if [ "$ERROR_LOG" == "true" ] ; then
		echo $RED_LOG"You Should check if this Package is System App or User Installed App."$COLOR_CLEAR > $LOG_DEV
		echo $RED_LOG"If it is System App, You should Add this Package to QB Fixed UID List - device/telechips/tcc89xx-common/quickboot_packages_lists.xml"$COLOR_CLEAR > $LOG_DEV
	fi
}

function qb_kmsg() {
	#	CMD : qb_kmsg
	(echo "======== qb_kmsg ========================================" &> $LOG_DEV)
	
	(echo "Start to qb_kmsg func!" &> $LOG_DEV)
	(/system/bin/qb_kmsg &> $LOG_DEV)

	(echo "End of qb_kmsg func!" &> $LOG_DEV)
}

if [ $SELECT_FUNC == "e2fsck_log" ]
	then
		e2fsck_log
elif [ $SELECT_FUNC == "attached_logs" ]
	then
		attached_logs $EXTRA_ARG
elif [ $SELECT_FUNC == "unmount" ]
	then
		unmount $EXTRA_ARG
elif [ $SELECT_FUNC == "check_fixed_uid" ]
	then
		check_fixed_uid $EXTRA_ARG
elif [ $SELECT_FUNC == "qb_kmsg" ]
	then
		qb_kmsg
else
	(echo "Invalid Func [$SELECT_FUNC]" &> $LOG_DEV)
fi



