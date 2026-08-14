#
# To learn more about a Podspec see http://guides.cocoapods.org/syntax/podspec.html.
# Run `pod lib lint universal_printer_flutter.podspec` to validate before publishing.
#
Pod::Spec.new do |s|
  s.name             = 'universal_printer_flutter'
  s.version          = '0.0.11'
  s.summary          = 'Discover and print ESC/POS receipts to network, USB, Star and Sunmi/iMin printers.'
  s.description      = <<-DESC
Cross-platform receipt/label printer plugin: discover (Epson/Sunmi/Star/SNMP/USB) and print ESC/POS
receipts to network, USB, Star and Sunmi/iMin printers. Full on Android; network + Star on iOS.
                       DESC
  s.homepage         = 'https://github.com/vikramvikraanth/-universal_printer_flutter'
  s.license          = { :file => '../LICENSE' }
  s.author           = { 'vikramvikraanth' => 'vikramvikraanth4@gmail.com' }
  s.source           = { :path => '.' }
  s.source_files = 'universal_printer_flutter/Sources/universal_printer_flutter/**/*'
  s.dependency 'Flutter'
  # StarXpand SDK (StarIO10) — hard dependency, mirroring the Android build's bundled StarXpand.
  # Star officially supports iOS 15+; if `pod install` reports a deployment-target conflict, raise
  # s.platform below to the StarIO10 pod's required minimum.
  s.dependency 'StarIO10'
  s.platform = :ios, '13.0'

  # Flutter.framework does not contain a i386 slice.
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES', 'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386' }
  s.swift_version = '5.0'

  # If your plugin requires a privacy manifest, for example if it uses any
  # required reason APIs, update the PrivacyInfo.xcprivacy file to describe your
  # plugin's privacy impact, and then uncomment this line. For more information,
  # see https://developer.apple.com/documentation/bundleresources/privacy_manifest_files
  # s.resource_bundles = {'universal_printer_flutter_privacy' => ['universal_printer_flutter/Sources/universal_printer_flutter/PrivacyInfo.xcprivacy']}
end
