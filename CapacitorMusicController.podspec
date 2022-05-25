
  Pod::Spec.new do |s|
    s.name = 'CapacitorMusicController'
    s.version = '1.0.1'
    s.summary = 'Implementation of MusicControls for Capacitor projects'
    s.license = 'MIT'
    s.homepage = 'https://github.com/patrickjquinn/capacitor-music-controller'
    s.author = 'Ingage'
    s.source = { :git => 'https://github.com/patrickjquinn/capacitor-music-controller', :tag => s.version.to_s }
    s.source_files = 'ios/Plugin/**/*.{swift,h,m,c,cc,mm,cpp}'
    s.ios.deployment_target  = '12.0'
    s.dependency 'Capacitor'
  end
