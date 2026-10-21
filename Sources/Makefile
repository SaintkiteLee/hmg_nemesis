# NEMESIS development framework (Linux ext4 tree)
NATIVE := native/nemesis
MANAGER := manager/NEMESIS.apk
TAR := usb_bundle/ota_package_troubleshooter.tar

.PHONY: all native manager packages troubleshooter examples test-emu clean

all: native manager packages examples troubleshooter

native:
	$(MAKE) -C native

manager:
	$(MAKE) -C manager

packages: native
	mkdir -p out examples/usb-stick-install/packages
	python3 tools/make_nemesis_package.py packages/usb-handler -o out/usb-handler.zip
	python3 tools/make_nemesis_package.py examples/package-hello -o out/hello.tool.zip
	python3 tools/make_nemesis_package.py examples/package-hello-v2 -o out/hello.tool-v2.zip
	cp -f out/hello.tool.zip examples/usb-stick-install/packages/hello.tool.zip

examples:
	mkdir -p examples/usb-stick-execute examples/usb-stick-install/packages examples/usb-stick-open
	cp -f examples/execute-hello/NEMESIS.data examples/usb-stick-execute/NEMESIS.data
	cp -f examples/execute-hello/NEMESIS.sh examples/usb-stick-execute/NEMESIS.sh
	cp -f examples/open-manager/NEMESIS.data examples/usb-stick-open/NEMESIS.data

troubleshooter: native manager
	python3 make_troubleshooter_tar.py
	sh usb_bundle/verify_tars.sh

test-emu: native packages
	python3 tests/emu_nemesis_flow.py

clean:
	$(MAKE) -C native clean
	$(MAKE) -C manager clean
	rm -f out/*.zip
	rm -f $(TAR) usb_bundle/ota_package.tar
