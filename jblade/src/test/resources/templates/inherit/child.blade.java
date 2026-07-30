@extends('inherit.middle')

@section('title', 'ChildTitle')

@section('sidebar')
@parent
CHILD
@endsection

@section('content')
Hello {{ $name }}
@endsection
