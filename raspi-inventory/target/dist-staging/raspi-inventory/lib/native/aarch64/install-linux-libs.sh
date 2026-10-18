#!/bin/bash

install_dir=""
call_ldconfig=0

function printUsage() {
  echo "install libraries: copy, set links and optionally call ldconfig"
  echo "Usage: install-linux-libs.sh [OPTIONS] destdir"
  echo  ""
  echo  "Options"
  echo  "  --register-libraries, -r          Call 'ldconfig' to register the libraries"
}

while (( "$#" )); do
  case "$1" in
    --register-libraries | --register | -r)
      call_ldconfig=1
      ;;
    --help | -? | -h)
      printUsage
      exit 0
      ;;
    *)
      if [[ -z "${install_dir}" ]]; then
        install_dir="$1";
      else
        printUsage
        exit 1
      fi
      ;;
  esac
  shift
done

if [[ -z "$install_dir" ]]; then
  printUsage
  exit 2
fi
echo "install libraries to path '$install_dir'"

for word in $(ls -l lib*.so* | grep '^-')
do
  if [ "${word:0:3}" == "lib" ]; then
    libfile=$word
    libminor=${libfile%.[0-9]*}
    libmajor=${libminor%.[0-9]*}
    libname=${libmajor%.[0-9]*}
    echo "$libname ==> $libmajor ==> $libfile"

    if [[ "${install_dir}" != "." ]]; then
        cp "${libfile}" "${install_dir}"
    fi
    ln -sf ${install_dir}/${libfile}  $install_dir/${libmajor}
    ln -sf ${install_dir}/${libmajor} $install_dir/${libname}
  fi
done

if [[ "${call_ldconfig}" -eq 1 ]]; then
  ldconfig "${install_dir}"
fi
